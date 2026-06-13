package com.flownetworks.bot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final String KEY_PREFIX = "bot:conversation:";
    private static final Duration TTL = Duration.ofHours(24);
    private static final int MAX_TURNS = 20;
    private static final int MAX_RETRY_ATTEMPTS = 5;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ConversationService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Trả về history dưới dạng List<Map> với keys "role" và "content"
     * để dễ serialize/deserialize
     */
    public List<Map<String, String>> getHistory(String conversationId) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + conversationId);
            if (json == null) {
                log.debug("No history found for conversation {}", conversationId);
                return new ArrayList<>();
            }
            List<Map<String, String>> history = objectMapper.readValue(json, new TypeReference<>() {});
            log.debug("Loaded {} messages from history for conversation {}", history.size(), conversationId);
            return history;
        } catch (Exception e) {
            log.warn("Failed to read conversation history for {}: {}", conversationId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public void appendMessage(String conversationId, String role, String content) {
        appendMessages(conversationId, List.of(Map.entry(role, content)));
    }

    /**
     * Append nhiều message trong 1 lần đọc/ghi Redis (giảm round-trip).
     * Mỗi entry là (role, content).
     */
    public void appendMessages(String conversationId, List<Map.Entry<String, String>> turns) {
        if (conversationId == null || conversationId.isBlank() || turns == null || turns.isEmpty()) {
            return;
        }

        List<Map<String, String>> newEntries = turns.stream()
            .map(turn -> {
                Map<String, String> message = new LinkedHashMap<>();
                message.put("role", turn.getKey());
                message.put("content", turn.getValue());
                return message;
            })
            .toList();

        String key = KEY_PREFIX + conversationId;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            final int attemptIndex = attempt;
            try {
                AtomicReference<List<Map<String, String>>> updatedHistoryRef = new AtomicReference<>();

                SessionCallback<Boolean> callback = new SessionCallback<>() {
                    @Override
                    public Boolean execute(org.springframework.data.redis.core.RedisOperations operations) throws DataAccessException {
                        operations.watch(key);
                        try {
                            String json = (String) operations.opsForValue().get(key);
                            List<Map<String, String>> history = deserializeHistory(json);
                            history.addAll(newEntries);

                            if (history.size() > MAX_TURNS) {
                                history = history.subList(history.size() - MAX_TURNS, history.size());
                                log.debug("Trimmed conversation {} to {} messages (attempt #{})",
                                    conversationId, history.size(), attemptIndex);
                            }

                            String serialized;
                            try {
                                serialized = objectMapper.writeValueAsString(history);
                            } catch (JsonProcessingException ex) {
                                throw new IllegalStateException("Failed to serialize conversation history", ex);
                            }
                            operations.multi();
                            operations.opsForValue().set(key, serialized, TTL);
                            List<Object> execResult = operations.exec();
                            if (execResult != null) {
                                updatedHistoryRef.set(history);
                                return true;
                            }
                            return false;
                        } finally {
                            operations.unwatch();
                        }
                    }
                };

                Boolean success = redisTemplate.execute(callback);

                if (Boolean.TRUE.equals(success)) {
                    List<Map<String, String>> updated = updatedHistoryRef.get();
                    log.debug("Saved {} message(s) to conversation {} (total: {} messages, TTL: {})",
                        turns.size(), conversationId, updated != null ? updated.size() : -1, TTL);
                    return;
                }

                log.debug("Conversation {} write conflict (attempt #{}) — retrying", conversationId, attempt);
            } catch (Exception e) {
                log.error("Failed to save conversation history for {} on attempt #{}: {}",
                    conversationId, attempt, e.getMessage());
                return;
            }
        }

        log.error("Failed to save conversation history for {} after {} attempts", conversationId, MAX_RETRY_ATTEMPTS);
    }

    public void clearHistory(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        redisTemplate.delete(KEY_PREFIX + conversationId);
        log.debug("Cleared conversation history for {}", conversationId);
    }

    private List<Map<String, String>> deserializeHistory(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize conversation history JSON ({} chars): {}", json.length(), e.getMessage());
            return new ArrayList<>();
        }
    }
}
