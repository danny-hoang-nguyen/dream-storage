package com.flownetworks.bot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    private static final String SYSTEM_PROMPT =
            "Bạn là trợ lý AI cá nhân hữu ích, thông minh và thân thiện. " +
            "Trả lời ngắn gọn, súc tích. Dùng code block khi minh hoạ code. " +
            "Trả lời bằng ngôn ngữ mà người dùng đang dùng.";

    public ClaudeService(
            RestTemplate externalApiRestTemplate,
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model:claude-sonnet-4-6}") String model,
            @Value("${anthropic.max-tokens:2048}") int maxTokens) {
        this.restTemplate = externalApiRestTemplate;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    public String chat(List<Map<String, String>> history, String userMessage) {
        log.info("Calling Claude | history={} messages | userMsg={} chars", history.size(), userMessage.length());
        long t0 = System.currentTimeMillis();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            List<Map<String, String>> messages = buildMessages(history, userMessage);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "system", SYSTEM_PROMPT,
                    "messages", messages
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(API_URL, request, Map.class);

            log.info("Claude done in {}ms", System.currentTimeMillis() - t0);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            if (content == null || content.isEmpty()) {
                return "Không có response từ Claude. Vui lòng thử lại.";
            }

            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> block : content) {
                if ("text".equals(block.get("type"))) {
                    sb.append(block.get("text"));
                }
            }
            String text = sb.toString().trim();
            return text.isEmpty() ? "Không có response từ Claude. Vui lòng thử lại." : text;

        } catch (Exception e) {
            log.error("Claude API call failed after {}ms: {}", System.currentTimeMillis() - t0, e.getMessage(), e);
            return "Xin lỗi, không thể kết nối Claude lúc này. Vui lòng thử lại.";
        }
    }

    private List<Map<String, String>> buildMessages(List<Map<String, String>> history, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (Map<String, String> msg : history) {
            messages.add(Map.of("role", msg.get("role"), "content", msg.get("content")));
        }
        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }
}
