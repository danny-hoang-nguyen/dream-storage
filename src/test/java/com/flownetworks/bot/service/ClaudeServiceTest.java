package com.flownetworks.bot.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
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
    private com.anthropic.services.blocking.MessageService messagesService;

    private ClaudeService claudeService;

    @BeforeEach
    void setUp() throws Exception {
        claudeService = new ClaudeService("test-api-key", "claude-sonnet-4-6", 2048);
        Field clientField = ClaudeService.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(claudeService, mockClient);
        lenient().when(mockClient.messages()).thenReturn(messagesService);
    }

    @Test
    void chat_returnsTextResponse() {
        ContentBlock textBlock = buildTextBlock("Hello! How can I help you?");
        Message message = buildMessage(List.of(textBlock), Message.StopReason.END_TURN, 10, 20);

        when(messagesService.create(any())).thenReturn(message);

        String result = claudeService.chat(new ArrayList<>(), "Hi");

        assertEquals("Hello! How can I help you?", result);
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
    void chat_multipleTextBlocks_joinsWithNewline() {
        ContentBlock block1 = buildTextBlock("Part 1");
        ContentBlock block2 = buildTextBlock("Part 2");
        Message message = buildMessage(List.of(block1, block2), Message.StopReason.END_TURN, 10, 20);

        when(messagesService.create(any())).thenReturn(message);

        String result = claudeService.chat(new ArrayList<>(), "Hi");

        assertEquals("Part 1\nPart 2", result);
    }

    @Test
    void chat_emptyHistory_works() {
        ContentBlock textBlock = buildTextBlock("Answer");
        Message message = buildMessage(List.of(textBlock), Message.StopReason.END_TURN, 5, 10);

        when(messagesService.create(any())).thenReturn(message);

        String result = claudeService.chat(new ArrayList<>(), "Question");

        assertEquals("Answer", result);
    }

    @Test
    void chat_assistantRoleInHistory_mappedCorrectly() {
        ContentBlock textBlock = buildTextBlock("Follow-up answer");
        Message message = buildMessage(List.of(textBlock), Message.StopReason.END_TURN, 10, 10);
        when(messagesService.create(any())).thenReturn(message);

        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "hello"),
                Map.of("role", "assistant", "content", "hi there"),
                Map.of("role", "user", "content", "how are you"),
                Map.of("role", "assistant", "content", "I am fine")
        );

        String result = claudeService.chat(history, "great");

        assertEquals("Follow-up answer", result);
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
}
