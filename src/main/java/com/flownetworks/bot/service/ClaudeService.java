package com.flownetworks.bot.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
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

    private final AnthropicClient client;
    private final String model;
    private final int maxTokens;

    private static final String SYSTEM_PROMPT = """
            Bạn là trợ lý AI cá nhân hữu ích, thông minh và thân thiện.
            Trả lời ngắn gọn, súc tích. Dùng code block khi minh hoạ code.
            Trả lời bằng ngôn ngữ mà người dùng đang dùng.
            """;

    public ClaudeService(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model:claude-sonnet-4-6}") String model,
            @Value("${anthropic.max-tokens:2048}") int maxTokens) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        this.model = model;
        this.maxTokens = maxTokens;
    }

    public String chat(List<Map<String, String>> history, String userMessage) {
        List<MessageParam> messages = buildMessages(history, userMessage);
        log.debug("Calling Claude | history={} messages | userMsg={} chars", history.size(), userMessage.length());

        long t0 = System.currentTimeMillis();
        Message response = client.messages().create(
                MessageCreateParams.builder()
                        .model(model)
                        .maxTokens(maxTokens)
                        .system(SYSTEM_PROMPT)
                        .messages(messages)
                        .build()
        );

        log.debug("Claude done in {}ms | input={} output={} tokens",
                System.currentTimeMillis() - t0,
                response.usage().inputTokens(), response.usage().outputTokens());

        String text = response.content().stream()
                .filter(ContentBlock::isText)
                .map(block -> block.asText().text())
                .collect(Collectors.joining("\n"))
                .trim();

        return text.isEmpty() ? "Không có response từ Claude. Vui lòng thử lại." : text;
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
}
