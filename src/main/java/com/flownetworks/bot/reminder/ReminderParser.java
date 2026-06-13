package com.flownetworks.bot.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReminderParser {

    private static final Logger log = LoggerFactory.getLogger(ReminderParser.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public ReminderParser(
            RestTemplate externalApiRestTemplate,
            ObjectMapper objectMapper,
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.reminder-model:claude-haiku-4-5-20251001}") String model) {
        this.restTemplate = externalApiRestTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public Optional<ParsedReminder> parse(String userMessage) {
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        String nowIso = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String system = """
                Bạn là bộ phân tích yêu cầu đặt nhắc nhở. Người dùng nói bằng tiếng Việt.
                Thời điểm hiện tại (Asia/Ho_Chi_Minh): %s.
                Phân tích tin nhắn xem có phải yêu cầu đặt 1 nhắc nhở (reminder) không.
                CHỈ trả về duy nhất 1 đối tượng JSON đúng schema, không markdown, không giải thích:
                {
                  "is_reminder": boolean,
                  "task": string (việc cần nhắc, ngắn gọn, tiếng Việt; "" nếu không phải reminder),
                  "remind_at": string (ISO-8601 có offset như "2026-06-13T20:00:00+07:00"; "" nếu không phải reminder),
                  "reply": string (câu xác nhận tiếng Việt thân thiện nếu là reminder; nếu không phải, "")
                }
                Quy ước:
                - "tối nay" = 20:00 hôm nay nếu chưa qua, nếu không thì là tối mai.
                - "mai" / "ngày mai" = ngày hôm sau theo Asia/Ho_Chi_Minh.
                - "8h", "8 giờ" không có "tối/sáng" → suy luận theo ngữ cảnh; sáng nếu trước 12h hiện tại còn xa, ngược lại tối.
                - Nếu thời gian mơ hồ hoặc đã ở quá khứ → is_reminder=false.
                """.formatted(nowIso);

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 400,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", userMessage))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    API_URL, new HttpEntity<>(body, headers), Map.class);
            if (response == null) return Optional.empty();

            String text = extractText(response);
            if (text == null || text.isBlank()) return Optional.empty();

            Matcher m = JSON_BLOCK.matcher(text);
            if (!m.find()) {
                log.debug("Reminder parse: no JSON block in response: {}", text);
                return Optional.empty();
            }
            JsonNode node = objectMapper.readTree(m.group());
            if (!node.path("is_reminder").asBoolean(false)) {
                return Optional.empty();
            }
            String task = node.path("task").asText("").trim();
            String remindAtRaw = node.path("remind_at").asText("").trim();
            String reply = node.path("reply").asText("").trim();
            if (task.isEmpty() || remindAtRaw.isEmpty()) return Optional.empty();

            long epochMs;
            try {
                epochMs = OffsetDateTime.parse(remindAtRaw).toInstant().toEpochMilli();
            } catch (Exception e) {
                log.debug("Reminder parse: bad ISO time '{}': {}", remindAtRaw, e.getMessage());
                return Optional.empty();
            }
            if (epochMs <= System.currentTimeMillis()) {
                log.debug("Reminder parse: time in the past ({})", remindAtRaw);
                return Optional.empty();
            }
            return Optional.of(new ParsedReminder(task, epochMs, reply));
        } catch (Exception e) {
            log.warn("Reminder parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        Object contentObj = response.get("content");
        if (!(contentObj instanceof List<?> content)) return null;
        StringBuilder sb = new StringBuilder();
        for (Object block : content) {
            if (block instanceof Map<?, ?> map && "text".equals(map.get("type"))) {
                Object t = map.get("text");
                if (t != null) sb.append(t);
            }
        }
        return sb.toString();
    }

    public record ParsedReminder(String task, long remindAtEpochMs, String reply) {}
}
