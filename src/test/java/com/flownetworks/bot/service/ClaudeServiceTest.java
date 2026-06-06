package com.flownetworks.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaudeServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private ClaudeService claudeService;

    @BeforeEach
    void setUp() throws Exception {
        var prompt = new ByteArrayResource("You are a test bot.".getBytes(StandardCharsets.UTF_8));
        claudeService = new ClaudeService(restTemplate, "test-api-key", "claude-sonnet-4-6", 2048, prompt);
    }

    @Test
    void chat_returnsTextResponse() {
        Map<String, Object> response = Map.of(
                "content", List.of(Map.of("type", "text", "text", "Hello! How can I help?"))
        );
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class))).thenReturn(response);

        String result = claudeService.chat(new ArrayList<>(), "Hi");

        assertEquals("Hello! How can I help?", result);
    }

    @Test
    void chat_emptyContent_returnsDefaultMessage() {
        Map<String, Object> response = Map.of("content", List.of());
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class))).thenReturn(response);

        String result = claudeService.chat(new ArrayList<>(), "Hi");

        assertEquals("Không có response từ Claude. Vui lòng thử lại.", result);
    }

    @Test
    void chat_nullResponse_returnsDefaultMessage() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class))).thenReturn(null);

        String result = claudeService.chat(new ArrayList<>(), "Hi");

        assertEquals("Không có response từ Claude. Vui lòng thử lại.", result);
    }

    @Test
    void chat_apiException_returnsErrorMessage() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        String result = claudeService.chat(new ArrayList<>(), "Hi");

        assertEquals("Xin lỗi, không thể kết nối Claude lúc này. Vui lòng thử lại.", result);
    }

    @Test
    void chat_withHistory_includesHistoryInRequest() {
        Map<String, Object> response = Map.of(
                "content", List.of(Map.of("type", "text", "text", "Follow-up answer"))
        );
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class))).thenReturn(response);

        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "first question"),
                Map.of("role", "assistant", "content", "first answer")
        );

        String result = claudeService.chat(history, "second question");

        assertEquals("Follow-up answer", result);
        verify(restTemplate, times(1)).postForObject(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void chat_multipleTextBlocks_joinsText() {
        Map<String, Object> response = Map.of(
                "content", List.of(
                        Map.of("type", "text", "text", "Part 1"),
                        Map.of("type", "text", "text", "Part 2")
                )
        );
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class))).thenReturn(response);

        String result = claudeService.chat(new ArrayList<>(), "Hi");

        assertEquals("Part 1Part 2", result);
    }
}
