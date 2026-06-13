package danny.project.chatbot.telegram.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import danny.project.chatbot.telegram.config.TelegramProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    private final RestTemplate restTemplate;
    private final TelegramProperties properties;

    public TelegramService(RestTemplate externalApiRestTemplate, TelegramProperties properties) {
        this.restTemplate = externalApiRestTemplate;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.hasValidBotToken();
    }

    public void sendMessage(long chatId, String text) {
        if (!isEnabled()) {
            log.debug("Telegram bot token not configured; skip sendMessage");
            return;
        }

        String html = convertMarkdownToHtml(text);

        MultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
        payload.add("chat_id", Long.toString(chatId));
        payload.add("text", html);
        payload.add("parse_mode", "HTML");

        callTelegram("sendMessage", payload);
    }

    // --- Markdown → Telegram HTML converter ---

    static String convertMarkdownToHtml(String text) {
        if (text == null || text.isBlank()) return text;

        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean inCode = false;
        StringBuilder codeBlock = new StringBuilder();
        List<String[]> tableBuffer = new ArrayList<>();

        for (String line : lines) {
            if (line.startsWith("```")) {
                flushTable(tableBuffer, out);
                tableBuffer.clear();
                if (!inCode) {
                    inCode = true;
                    codeBlock.setLength(0);
                } else {
                    inCode = false;
                    out.append("<pre>")
                       .append(escapeHtml(codeBlock.toString().stripTrailing()))
                       .append("</pre>\n");
                }
                continue;
            }
            if (inCode) {
                codeBlock.append(line).append("\n");
                continue;
            }

            // Table rows (lines starting with |)
            String trimmed = line.trim();
            if (trimmed.startsWith("|")) {
                // Separator row (e.g. |---|---|): only contains |, -, :, space
                if (trimmed.replaceAll("[|:\\-\\s]", "").isEmpty()) {
                    continue; // skip separator
                }
                tableBuffer.add(splitTableRow(trimmed));
                continue;
            }

            // Non-table line: flush any accumulated table rows first
            if (!tableBuffer.isEmpty()) {
                flushTable(tableBuffer, out);
                tableBuffer.clear();
            }

            out.append(convertLine(line)).append("\n");
        }

        flushTable(tableBuffer, out);

        // Unclosed code block
        if (inCode && codeBlock.length() > 0) {
            out.append("<pre>").append(escapeHtml(codeBlock.toString().stripTrailing())).append("</pre>\n");
        }

        String result = out.toString().trim();
        // Telegram hard limit: 4096 chars
        return result.length() > 4000 ? result.substring(0, 4000) + "\n..." : result;
    }

    private static String[] splitTableRow(String line) {
        String s = line;
        if (s.startsWith("|")) s = s.substring(1);
        if (s.endsWith("|")) s = s.substring(0, s.length() - 1);
        return Arrays.stream(s.split("\\|", -1))
                .map(String::trim)
                .toArray(String[]::new);
    }

    private static void flushTable(List<String[]> rows, StringBuilder out) {
        if (rows.isEmpty()) return;
        boolean header = true;
        for (String[] cells : rows) {
            String rendered = Arrays.stream(cells)
                    .map(TelegramService::processInline)
                    .collect(Collectors.joining(" | "));
            if (header) {
                out.append("<b>").append(rendered).append("</b>\n");
                header = false;
            } else {
                out.append("• ").append(rendered).append("\n");
            }
        }
        out.append("\n");
    }

    private static String convertLine(String line) {
        // Horizontal rule
        if (line.matches("^[-*_]{3,}\\s*$")) return "";

        // Headers → bold
        if (line.startsWith("### ")) return "<b>" + processInline(line.substring(4)) + "</b>";
        if (line.startsWith("## "))  return "<b>" + processInline(line.substring(3)) + "</b>";
        if (line.startsWith("# "))   return "<b>" + processInline(line.substring(2)) + "</b>";

        // Bullet point (- item / * item / • item)
        if (line.matches("^[-*•] .+")) return "• " + processInline(line.substring(2));

        return processInline(line);
    }

    private static String processInline(String text) {
        text = escapeHtml(text);
        // Bold **text**
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        // Italic _text_ (single underscore, not adjacent to word chars)
        text = text.replaceAll("(?<![_\\w])_([^_\n]+)_(?![_\\w])", "<i>$1</i>");
        // Inline code `text`
        text = text.replaceAll("`([^`\n]+)`", "<code>$1</code>");
        // Links [text](url)
        text = text.replaceAll("\\[([^\\]]+)]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");
        // Bare: WORD (https://...) → clickable link
        text = text.replaceAll("(\\S+)\\s+\\((https?://[^)\\s]+)\\)", "<a href=\"$2\">$1</a>");
        return text;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public void sendTypingAction(long chatId) {
        if (!isEnabled()) {
            return;
        }

        MultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
        payload.add("chat_id", Long.toString(chatId));
        payload.add("action", "typing");

        callTelegram("sendChatAction", payload);
    }

    private void callTelegram(String method, MultiValueMap<String, String> payload) {
        String url = String.format("https://api.telegram.org/bot%s/%s", properties.getBotToken(), method);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<TelegramResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                TelegramResponse.class
            );

            if (response.getBody() != null && !response.getBody().ok) {
                log.warn("Telegram API {} returned error: {}", method, response.getBody().description);
            }
        } catch (Exception e) {
            log.error("Telegram API {} call failed: {}", method, e.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TelegramResponse {
        @JsonProperty("ok")
        private boolean ok;

        @JsonProperty("description")
        private String description;
    }
}
