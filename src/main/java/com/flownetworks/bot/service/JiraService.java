package com.flownetworks.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flownetworks.bot.dto.JiraIssue;
import com.flownetworks.bot.dto.JiraSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class JiraService {

    private static final Logger log = LoggerFactory.getLogger(JiraService.class);

    private static final int MAX_QUERY_LENGTH = 200;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final HttpHeaders headers;
    private final String baseUrl;
    private final String projectKey;

    public JiraService(
            RestTemplate externalApiRestTemplate,
            ObjectMapper objectMapper,
            @Value("${jira.base-url}") String baseUrl,
            @Value("${jira.username}") String username,
            @Value("${jira.api-token}") String apiToken,
            @Value("${jira.project-key:}") String projectKey) {

        this.baseUrl = baseUrl;
        this.projectKey = projectKey;
        this.restTemplate = externalApiRestTemplate;
        this.objectMapper = objectMapper;

        String credentials = username + ":" + apiToken;
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        this.headers = new HttpHeaders();
        this.headers.set("Authorization", "Basic " + encoded);
        this.headers.set("Accept", "application/json");
        this.headers.setContentType(MediaType.APPLICATION_JSON);
    }

    /**
     * Search Jira issues bằng JQL (Jira Query Language)
     * Tìm tickets liên quan theo keyword trong summary và description
     */
    public List<JiraIssue> search(String keyword, int maxResults) {
        try {
            // Build JQL query
            String jql = buildJql(keyword);
            log.debug("Jira JQL: {}", jql);

            URI uri = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/rest/api/3/search/jql")
                .queryParam("jql", jql)
                .queryParam("maxResults", maxResults)
                .queryParam("fields", "summary,status,priority,description")
                .build()
                .encode()
                .toUri();
            log.debug("Jira search URI: {}", uri);

            ResponseEntity<JiraSearchResponse> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JiraSearchResponse.class
            );

            if (response.getBody() == null || response.getBody().getIssues() == null) {
                log.debug("Jira search: empty response for keyword='{}'", keyword);
                return Collections.emptyList();
            }

            List<JiraIssue> issues = response.getBody().getIssues().stream()
                .map(issue -> new JiraIssue(
                    issue.getKey(),
                    issue.getFields().getSummary(),
                    issue.getFields().getStatus() != null ? issue.getFields().getStatus().getName() : "Unknown",
                    issue.getFields().getPriority() != null ? issue.getFields().getPriority().getName() : "None",
                    extractDescription(issue.getFields().getDescription()),
                    baseUrl + "/browse/" + issue.getKey()
                ))
                .toList();

            log.debug("Jira search returned {} issues for keyword='{}'", issues.size(), keyword);
            return issues;

        } catch (Exception e) {
            log.warn("Jira search failed for '{}': {}", keyword, e.getMessage());
            throw new ToolExecutionException("Jira API search thất bại", e);
        }
    }

    private String buildJql(String keyword) {
        String sanitized = sanitizeQueryTerm(keyword);

        StringBuilder jql = new StringBuilder();
        if (!sanitized.isBlank()) {
            jql.append("(summary ~ \"").append(sanitized).append("\"")
               .append(" OR description ~ \"").append(sanitized).append("\")");
        } else {
            jql.append("summary IS NOT EMPTY");
        }

        if (projectKey != null && !projectKey.isBlank()) {
            jql.append(" AND project = \"").append(projectKey).append("\"");
        }

        jql.append(" ORDER BY updated DESC");
        return jql.toString();
    }

    private String sanitizeQueryTerm(String keyword) {
        if (keyword == null) {
            return "";
        }

        String normalized = Normalizer.normalize(keyword, Normalizer.Form.NFKC);
        String cleaned = normalized
            .replaceAll("[\"\\\\]", " ")
            .replaceAll("[^\\p{L}\\p{N}\\s-]", " ")
            .replaceAll("\\s+", " ")
            .trim();

        if (cleaned.length() > MAX_QUERY_LENGTH) {
            cleaned = cleaned.substring(0, MAX_QUERY_LENGTH).trim();
        }

        return cleaned;
    }

    /**
     * Jira description có thể là plain text hoặc Atlassian Document Format (ADF - JSON)
     * Hàm này extract text từ cả 2 format
     */
    @SuppressWarnings("unchecked")
    private String extractDescription(Object description) {
        if (description == null) return "";

        // Plain text
        if (description instanceof String s) {
            log.debug("Jira description format: plain text ({} chars)", s.length());
            return truncate(s, 500);
        }

        // ADF format (nested JSON) — extract text nodes đệ quy
        try {
            log.debug("Jira description format: ADF");
            String json = objectMapper.writeValueAsString(description);
            Map<String, Object> adf = objectMapper.readValue(json, Map.class);
            StringBuilder sb = new StringBuilder();
            extractAdfText(adf, sb);
            return truncate(sb.toString().trim(), 500);
        } catch (Exception e) {
            log.debug("Jira description ADF parse failed: {}", e.getMessage());
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private void extractAdfText(Map<String, Object> node, StringBuilder sb) {
        // Nếu là text node thì lấy text
        if ("text".equals(node.get("type")) && node.get("text") instanceof String text) {
            sb.append(text).append(" ");
        }
        // Đệ quy vào content children
        if (node.get("content") instanceof List<?> children) {
            for (Object child : children) {
                if (child instanceof Map<?, ?> childMap) {
                    extractAdfText((Map<String, Object>) childMap, sb);
                }
            }
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
