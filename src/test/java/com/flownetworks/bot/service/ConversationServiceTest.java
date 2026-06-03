package com.flownetworks.bot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.SessionCallback;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<String> jsonCaptor;

    private ConversationService conversationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(redisTemplate, objectMapper);
    }

    @Test
    void getHistory_existingConversation_returnsParsedList() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(
                Map.of("role", "user", "content", "hello"),
                Map.of("role", "assistant", "content", "hi there")
        ));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("bot:conversation:conv-1")).thenReturn(json);

        List<Map<String, String>> history = conversationService.getHistory("conv-1");

        assertEquals(2, history.size());
        assertEquals("user", history.get(0).get("role"));
        assertEquals("hello", history.get(0).get("content"));
    }

    @Test
    void getHistory_noHistory_returnsEmptyList() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("bot:conversation:conv-2")).thenReturn(null);

        List<Map<String, String>> history = conversationService.getHistory("conv-2");

        assertTrue(history.isEmpty());
    }

    @Test
    void getHistory_corruptedJson_returnsEmptyList() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("bot:conversation:conv-3")).thenReturn("not-json");

        List<Map<String, String>> history = conversationService.getHistory("conv-3");

        assertTrue(history.isEmpty());
    }

    @Test
    void getHistory_redisException_returnsEmptyList() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("bot:conversation:conv-4")).thenThrow(new RuntimeException("Redis connection failed"));

        List<Map<String, String>> history = conversationService.getHistory("conv-4");

        assertTrue(history.isEmpty());
    }

    @Test
    void appendMessages_withNullConversationId_doesNothing() {
        conversationService.appendMessages(null, List.of(Map.entry("user", "hello")));

        verify(redisTemplate, never()).execute(any(SessionCallback.class));
    }

    @Test
    void appendMessages_withEmptyTurns_doesNothing() {
        conversationService.appendMessages("conv-1", List.of());

        verify(redisTemplate, never()).execute(any(SessionCallback.class));
    }

    @Test
    void appendMessages_withNullTurns_doesNothing() {
        conversationService.appendMessages("conv-1", null);

        verify(redisTemplate, never()).execute(any(SessionCallback.class));
    }

    @Test
    void appendMessage_delegatesToAppendMessages() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(true);

        conversationService.appendMessage("conv-1", "user", "test message");

        verify(redisTemplate).execute(any(SessionCallback.class));
    }

    @Test
    void clearHistory_deletesKey() {
        when(redisTemplate.delete("bot:conversation:conv-1")).thenReturn(true);

        conversationService.clearHistory("conv-1");

        verify(redisTemplate).delete("bot:conversation:conv-1");
    }

    @Test
    void clearHistory_withNullId_doesNothing() {
        conversationService.clearHistory(null);

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void appendMessages_successfulWrite_savesWithTtl() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenAnswer(invocation -> {
            SessionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.execute(redisTemplate);
        });
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        conversationService.appendMessages("conv-1", List.of(Map.entry("user", "hello")));

        verify(valueOps).set(eq("bot:conversation:conv-1"), anyString(), eq(Duration.ofHours(2)));
    }

    @Test
    void appendMessages_redisException_duringExecute_returnsGracefully() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenThrow(new RuntimeException("Connection lost"));

        conversationService.appendMessages("conv-1", List.of(Map.entry("user", "hello")));

        verify(redisTemplate, times(1)).execute(any(SessionCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void appendMessages_writeConflict_retries() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(null, null, true);

        conversationService.appendMessages("conv-1", List.of(Map.entry("user", "hello")));

        verify(redisTemplate, times(3)).execute(any(SessionCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void appendMessages_exhaustsRetries_logsError() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(null);

        conversationService.appendMessages("conv-1", List.of(Map.entry("user", "hello")));

        verify(redisTemplate, times(5)).execute(any(SessionCallback.class));
    }

    @Test
    void appendMessage_successful_returnsNormally() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(true);

        conversationService.appendMessage("conv-1", "user", "hello");

        verify(redisTemplate).execute(any(SessionCallback.class));
    }

    @Test
    void getHistory_withoutLeadingPrefix_usesCorrectKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("bot:conversation:custom-id")).thenReturn("[]");

        List<Map<String, String>> history = conversationService.getHistory("custom-id");

        assertTrue(history.isEmpty());
        verify(valueOps).get("bot:conversation:custom-id");
    }
}
