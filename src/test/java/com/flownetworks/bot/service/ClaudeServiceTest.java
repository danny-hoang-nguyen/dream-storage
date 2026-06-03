package com.flownetworks.bot.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;
import com.anthropic.core.JsonValue;
import com.flownetworks.bot.dto.CodaDoc;
import com.flownetworks.bot.dto.JiraIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaudeServiceTest {

    @Mock
    private AnthropicClient mockClient;

    @Mock
    private CodaService codaService;

    @Mock
    private JiraService jiraService;

    @Mock
    private com.anthropic.services.blocking.MessageService messagesService;

    private ClaudeService claudeService;

    @BeforeEach
    void setUp() throws Exception {
        claudeService = new ClaudeService("test-api-key", "claude-sonnet-4-6", codaService, jiraService);
        Field clientField = ClaudeService.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(claudeService, mockClient);
        lenient().when(mockClient.messages()).thenReturn(messagesService);
    }

    @Test
    void chat_noToolUse_returnsTextResponse() {
        ContentBlock textBlock = buildTextBlock("Hello! How can I help you?");
        Message message = buildMessage(List.of(textBlock), Message.StopReason.END_TURN, 10, 20);

        when(messagesService.create(any())).thenReturn(message);

        String result = claudeService.chat(new ArrayList<>(), "Hi");

        assertEquals("Hello! How can I help you?", result);
    }

    @Test
    void chat_withToolUseLoop_completesFullCycle() {
        ContentBlock toolUseBlock = buildToolUseBlock("search_coda", "toolu_123",
                JsonValue.from(Map.of("query", "api docs")));
        ContentBlock textBlock = buildTextBlock("Here are the results");

        Message toolResponse = buildMessage(List.of(toolUseBlock), Message.StopReason.TOOL_USE, 15, 30);
        Message finalResponse = buildMessage(List.of(textBlock), Message.StopReason.END_TURN, 20, 10);

        when(codaService.search(anyString(), anyInt())).thenReturn(List.of(
                new CodaDoc("API Docs", "Guide for REST API", "https://coda.io/doc", "doc-1")
        ));

        when(messagesService.create(any()))
                .thenReturn(toolResponse)
                .thenReturn(finalResponse);

        String result = claudeService.chat(new ArrayList<>(), "Find API docs");

        assertEquals("Here are the results", result);
        verify(codaService, times(1)).search("api docs", 3);
    }

    @Test
    void chat_withMultipleToolCalls_executesAll() {
        ContentBlock codaTool = buildToolUseBlock("search_coda", "toolu_1",
                JsonValue.from(Map.of("query", "api")));
        ContentBlock jiraTool = buildToolUseBlock("search_jira", "toolu_2",
                JsonValue.from(Map.of("query", "bug")));
        ContentBlock textBlock = buildTextBlock("Found results in both");

        Message toolResponse = buildMessage(List.of(codaTool, jiraTool), Message.StopReason.TOOL_USE, 20, 40);
        Message finalResponse = buildMessage(List.of(textBlock), Message.StopReason.END_TURN, 25, 15);

        when(codaService.search(anyString(), anyInt())).thenReturn(List.of(
                new CodaDoc("API Docs", "Guide", "https://coda.io/doc", "doc-1")
        ));
        when(jiraService.search(anyString(), anyInt())).thenReturn(List.of(
                new JiraIssue("PROJ-1", "Fix bug", "Open", "High", "Bug desc", "https://jira/browse/PROJ-1")
        ));

        when(messagesService.create(any()))
                .thenReturn(toolResponse)
                .thenReturn(finalResponse);

        String result = claudeService.chat(new ArrayList<>(), "Search both");

        assertEquals("Found results in both", result);
        verify(codaService).search("api", 3);
        verify(jiraService).search("bug", 5);
    }

    @Test
    void chat_unknownTool_returnsErrorMessageInLoopThenFinalAnswer() {
        ContentBlock unknownTool = buildToolUseBlock("unknown_tool", "toolu_unknown",
                JsonValue.from(Map.of("query", "test")));
        ContentBlock textBlock = buildTextBlock("Final answer");

        Message toolResponse = buildMessage(List.of(unknownTool), Message.StopReason.TOOL_USE, 10, 20);
        Message finalResponse = buildMessage(List.of(textBlock), Message.StopReason.END_TURN, 15, 10);

        when(messagesService.create(any()))
                .thenReturn(toolResponse)
                .thenReturn(finalResponse);

        String result = claudeService.chat(new ArrayList<>(), "Test unknown tool");

        assertEquals("Final answer", result);
    }

    @Test
    void chat_toolExecutionException_returnsErrorResult() {
        ContentBlock toolBlock = buildToolUseBlock("search_coda", "toolu_err",
                JsonValue.from(Map.of("query", "test")));
        ContentBlock textBlock = buildTextBlock("Final");

        Message toolResponse = buildMessage(List.of(toolBlock), Message.StopReason.TOOL_USE, 10, 20);
        Message finalResponse = buildMessage(List.of(textBlock), Message.StopReason.END_TURN, 15, 10);

        when(codaService.search(anyString(), anyInt())).thenThrow(new ToolExecutionException("Coda API error"));
        when(messagesService.create(any()))
                .thenReturn(toolResponse)
                .thenReturn(finalResponse);

        String result = claudeService.chat(new ArrayList<>(), "Test error");
        assertEquals("Final", result);
    }

    @Test
    void chat_emptyTextResponse_returnsDefaultMessage() {
        ContentBlock emptyBlock = buildTextBlock("");
        Message message = buildMessage(List.of(emptyBlock), Message.StopReason.END_TURN, 5, 5);

        when(messagesService.create(any())).thenReturn(message);

        String result = claudeService.chat(new ArrayList<>(), "Hi");

        assertEquals("Không có response từ Claude. Vui lòng thử lại.", result);
    }

    @Test
    void chat_reachesMaxToolLoops_warnsAndReturns() {
        ContentBlock toolBlock = buildToolUseBlock("search_coda", "toolu_loop",
                JsonValue.from(Map.of("query", "loop")));
        ContentBlock finalBlock = buildTextBlock("Final after max loops");

        when(codaService.search(anyString(), anyInt())).thenReturn(List.of());

        when(messagesService.create(any()))
                .thenAnswer(invocation -> buildMessage(List.of(toolBlock), Message.StopReason.TOOL_USE, 10, 20))
                .thenAnswer(invocation -> buildMessage(List.of(toolBlock), Message.StopReason.TOOL_USE, 10, 20))
                .thenAnswer(invocation -> buildMessage(List.of(toolBlock), Message.StopReason.TOOL_USE, 10, 20))
                .thenAnswer(invocation -> buildMessage(List.of(toolBlock), Message.StopReason.TOOL_USE, 10, 20))
                .thenAnswer(invocation -> buildMessage(List.of(toolBlock), Message.StopReason.TOOL_USE, 10, 20))
                .thenAnswer(invocation -> buildMessage(List.of(toolBlock), Message.StopReason.TOOL_USE, 10, 20))
                .thenAnswer(invocation -> buildMessage(List.of(toolBlock), Message.StopReason.TOOL_USE, 10, 20))
                .thenAnswer(invocation -> buildMessage(List.of(toolBlock), Message.StopReason.TOOL_USE, 10, 20))
                .thenAnswer(invocation -> buildMessage(List.of(toolBlock), Message.StopReason.TOOL_USE, 10, 20))
                .thenAnswer(invocation -> buildMessage(List.of(toolBlock), Message.StopReason.TOOL_USE, 10, 20))
                .thenAnswer(invocation -> buildMessage(List.of(finalBlock), Message.StopReason.END_TURN, 100, 50));

        String result = claudeService.chat(new ArrayList<>(), "Loop test");
        assertEquals("Final after max loops", result);
    }

    @Test
    void chat_withHistory_buildsMessagesCorrectly() {
        ContentBlock textBlock = buildTextBlock("Reply");
        Message message = buildMessage(List.of(textBlock), Message.StopReason.END_TURN, 10, 10);
        when(messagesService.create(any())).thenReturn(message);

        List<Map<String, String>> history = new ArrayList<>();
        history.add(Map.of("role", "user", "content", "first question"));
        history.add(Map.of("role", "assistant", "content", "first answer"));

        String result = claudeService.chat(history, "second question");

        assertEquals("Reply", result);
        verify(messagesService, times(1)).create(any());
    }

    @Test
    void chat_withJiraTool_callsJiraService() {
        ContentBlock jiraTool = buildToolUseBlock("search_jira", "toolu_jira",
                JsonValue.from(Map.of("query", "login bug")));
        ContentBlock textBlock = buildTextBlock("Found ticket");

        Message toolResponse = buildMessage(List.of(jiraTool), Message.StopReason.TOOL_USE, 10, 20);
        Message finalResponse = buildMessage(List.of(textBlock), Message.StopReason.END_TURN, 15, 10);

        when(jiraService.search(anyString(), anyInt())).thenReturn(List.of(
                new JiraIssue("PROJ-42", "Login bug", "Open", "High",
                        "Users cannot login", "https://jira/browse/PROJ-42")
        ));

        when(messagesService.create(any()))
                .thenReturn(toolResponse)
                .thenReturn(finalResponse);

        String result = claudeService.chat(new ArrayList<>(), "Find login bug ticket");

        assertEquals("Found ticket", result);
        verify(jiraService).search("login bug", 5);
    }

    @Test
    void chat_toolExecutionException_catchAll_returnsErrorMessage() {
        ContentBlock toolBlock = buildToolUseBlock("search_coda", "toolu_null",
                JsonValue.from(Map.of("query", "test")));
        ContentBlock textBlock = buildTextBlock("Final answer");

        Message toolResponse = buildMessage(List.of(toolBlock), Message.StopReason.TOOL_USE, 10, 20);
        Message finalResponse = buildMessage(List.of(textBlock), Message.StopReason.END_TURN, 15, 10);

        when(codaService.search(anyString(), anyInt())).thenThrow(new NullPointerException("Unexpected null"));
        when(messagesService.create(any()))
                .thenReturn(toolResponse)
                .thenReturn(finalResponse);

        String result = claudeService.chat(new ArrayList<>(), "Test");
        assertEquals("Final answer", result);
    }

    private static Message buildMessage(List<ContentBlock> content, Message.StopReason stopReason,
                                         int inputTokens, int outputTokens) {
        return Message.builder()
                .id("msg_123")
                .content(content)
                .model("claude-sonnet-4-6")
                .role(JsonValue.from("assistant"))
                .stopReason(stopReason)
                .stopSequence((String) null)
                .usage(Usage.builder()
                        .inputTokens(inputTokens)
                        .outputTokens(outputTokens)
                        .cacheCreationInputTokens(0L)
                        .cacheReadInputTokens(0L)
                        .build())
                .build();
    }

    private static ContentBlock buildTextBlock(String text) {
        TextBlock textBlock = TextBlock.builder()
                .text(text)
                .citations(List.of())
                .build();
        return ContentBlock.ofText(textBlock);
    }

    private static ContentBlock buildToolUseBlock(String name, String id, JsonValue input) {
        ToolUseBlock toolUseBlock = ToolUseBlock.builder()
                .name(name)
                .id(id)
                .input(input)
                .build();
        return ContentBlock.ofToolUse(toolUseBlock);
    }
}
