package com.flownetworks.bot.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.flownetworks.bot.dto.CodaDoc;
import com.flownetworks.bot.dto.JiraIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);
    private static final int MAX_TOOL_LOOPS = 10;

    private final AnthropicClient client;
    private final String model;
    private final CodaService codaService;
    private final JiraService jiraService;

    private static final String SYSTEM_PROMPT = """
            Bạn là trợ lý kỹ thuật cho backend developer. Người dùng là các backend developer \
            cần hỗ trợ về code, architecture, debugging, và quy trình làm việc trong team.

            Bạn có 2 tools để tra cứu thông tin nội bộ:
            - search_coda: Tìm tài liệu kỹ thuật (API docs, ADR, runbook, hướng dẫn nội bộ)
            - search_jira: Tìm Jira tickets (bugs, features, tasks)

            Khi nào dùng tools:
            - Câu hỏi liên quan đến tài liệu, quy trình nội bộ → search_coda
            - Câu hỏi về bug, task, feature đang làm → search_jira
            - Có thể dùng cả 2 nếu cần thiết
            - Câu hỏi chào hỏi, câu hỏi kỹ thuật chung → trả lời ngay, không cần search

            Khi trả lời:
            - Nếu search có kết quả: tóm tắt thông tin và trích dẫn link để đọc thêm
            - Nếu có ticket liên quan: đề cập ticket key (VD: PROJ-123) và trạng thái
            - Kỹ thuật, súc tích — dùng code block khi minh hoạ
            - Trả lời tiếng Việt, technical terms giữ tiếng Anh
            """;

    private static final List<ToolUnion> TOOLS = List.of(
        ToolUnion.ofTool(Tool.builder()
            .name("search_coda")
            .description("Tìm tài liệu kỹ thuật nội bộ trong Coda: API docs, ADR, runbook, hướng dẫn")
            .inputSchema(Tool.InputSchema.builder()
                .type(JsonValue.from("object"))
                .properties(JsonValue.from(Map.of(
                    "query", Map.of(
                        "type", "string",
                        "description", "Từ khóa hoặc câu hỏi cần tìm trong Coda"
                    )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of("query")))
                .build())
            .build()),
        ToolUnion.ofTool(Tool.builder()
            .name("search_jira")
            .description("Tìm Jira tickets liên quan đến bug, feature, hoặc task")
            .inputSchema(Tool.InputSchema.builder()
                .type(JsonValue.from("object"))
                .properties(JsonValue.from(Map.of(
                    "query", Map.of(
                        "type", "string",
                        "description", "Mô tả vấn đề hoặc từ khóa để tìm ticket"
                    )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of("query")))
                .build())
            .build())
    );

    public ClaudeService(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model:claude-sonnet-4-6}") String model,
            CodaService codaService,
            JiraService jiraService) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        this.model = model;
        this.codaService = codaService;
        this.jiraService = jiraService;
    }

    public String chat(List<Map<String, String>> history, String userMessage) {
        List<MessageParam> messages = buildMessages(history, userMessage);
        log.debug("Calling Claude | history={} messages | userMsg={} chars", history.size(), userMessage.length());

        long t0 = System.currentTimeMillis();
        Message response = callClaude(messages);
        int loopCount = 0;

        // Agentic loop: Claude calls tools until it has a final answer
        // (giới hạn MAX_TOOL_LOOPS để tránh loop vô hạn / tốn token)
        while (response.stopReason()
                .map(Message.StopReason.TOOL_USE::equals)
                .orElse(false)
                && loopCount < MAX_TOOL_LOOPS) {

            loopCount++;
            log.debug("Tool use loop #{}", loopCount);

            // Reconstruct assistant message (may contain text + tool_use blocks)
            List<ContentBlockParam> assistantContent = response.content().stream()
                    .map(block -> block.isToolUse()
                            ? ContentBlockParam.ofToolUse(block.asToolUse().toParam())
                            : ContentBlockParam.ofText(TextBlockParam.builder()
                                    .text(block.asText().text())
                                    .build()))
                    .toList();
            messages.add(MessageParam.builder()
                    .role(MessageParam.Role.ASSISTANT)
                    .contentOfBlockParams(assistantContent)
                    .build());

            // Execute each tool and collect results
            List<ContentBlockParam> toolResults = response.content().stream()
                    .filter(ContentBlock::isToolUse)
                    .map(block -> {
                        ToolUseBlock toolUse = block.asToolUse();
                        String result = executeTool(toolUse.name(), toolUse._input());
                        log.debug("Tool {} -> {} chars", toolUse.name(), result.length());
                        return ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder()
                                        .toolUseId(toolUse.id())
                                        .content(result)
                                        .build());
                    })
                    .toList();

            messages.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(toolResults)
                    .build());

            response = callClaude(messages);
        }

        if (loopCount >= MAX_TOOL_LOOPS
                && response.stopReason().map(Message.StopReason.TOOL_USE::equals).orElse(false)) {
            log.warn("Reached MAX_TOOL_LOOPS ({}) — trả về kết quả hiện tại", MAX_TOOL_LOOPS);
        }

        log.debug("Claude done in {}ms | loops={} | input={} output={} tokens",
                System.currentTimeMillis() - t0, loopCount,
                response.usage().inputTokens(), response.usage().outputTokens());

        String text = response.content().stream()
                .filter(ContentBlock::isText)
                .map(block -> block.asText().text())
                .collect(Collectors.joining("\n"))
                .trim();

        return text.isEmpty() ? "Không có response từ Claude. Vui lòng thử lại." : text;
    }

    private Message callClaude(List<MessageParam> messages) {
        return client.messages().create(
                MessageCreateParams.builder()
                        .model(model)
                        .maxTokens(1500)
                        .system(SYSTEM_PROMPT)
                        .tools(TOOLS)
                        .messages(messages)
                        .build()
        );
    }

    private List<MessageParam> buildMessages(List<Map<String, String>> history, String userMessage) {
        List<MessageParam> messages = new ArrayList<>();
        for (Map<String, String> msg : history) {
            messages.add(MessageParam.builder()
                    .role("user".equals(msg.get("role"))
                            ? MessageParam.Role.USER
                            : MessageParam.Role.ASSISTANT)
                    .content(msg.get("content"))
                    .build());
        }
        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(userMessage)
                .build());
        return messages;
    }

    @SuppressWarnings("unchecked")
    private String executeTool(String name, JsonValue input) {
        try {
            Map<String, Object> inputMap = input.convert(Map.class);
            String query = (String) inputMap.get("query");

            return switch (name) {
                case "search_coda" -> {
                    long t = System.currentTimeMillis();
                    List<CodaDoc> docs = codaService.search(query, 3);
                    log.debug("search_coda \"{}\" took {}ms, found {} docs",
                            query, System.currentTimeMillis() - t, docs.size());
                    yield formatCodaResults(docs);
                }
                case "search_jira" -> {
                    long t = System.currentTimeMillis();
                    List<JiraIssue> issues = jiraService.search(query, 5);
                    log.debug("search_jira \"{}\" took {}ms, found {} issues",
                            query, System.currentTimeMillis() - t, issues.size());
                    yield formatJiraResults(issues);
                }
                default -> "Tool không tồn tại: " + name;
            };
        } catch (ToolExecutionException e) {
            log.warn("Tool {} execution failed: {}", name, e.getMessage(), e);
            return "Tool " + name + " đang gặp sự cố: " + e.getMessage();
        } catch (Exception e) {
            log.warn("Tool {} execution failed: {}", name, e.getMessage(), e);
            return "Lỗi khi thực thi tool " + name + ": " + e.getMessage();
        }
    }

    private String formatCodaResults(List<CodaDoc> docs) {
        if (docs.isEmpty()) return "Không tìm thấy tài liệu liên quan trong Coda.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            CodaDoc doc = docs.get(i);
            sb.append("Doc ").append(i + 1).append(": ").append(doc.title()).append("\n");
            sb.append("Link: ").append(doc.url()).append("\n");
            if (doc.excerpt() != null && !doc.excerpt().isBlank()) {
                sb.append("Tóm tắt: ").append(doc.excerpt()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatJiraResults(List<JiraIssue> issues) {
        if (issues.isEmpty()) return "Không tìm thấy ticket liên quan trong Jira.";
        StringBuilder sb = new StringBuilder();
        for (JiraIssue issue : issues) {
            sb.append("Ticket: ").append(issue.key()).append(" — ").append(issue.summary()).append("\n");
            sb.append("Status: ").append(issue.status()).append(" | Priority: ").append(issue.priority()).append("\n");
            sb.append("Link: ").append(issue.url()).append("\n");
            if (issue.description() != null && !issue.description().isBlank()) {
                sb.append("Description: ").append(issue.description()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
