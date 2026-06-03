package com.flownetworks.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flownetworks.bot.dto.JiraIssue;
import com.flownetworks.bot.dto.JiraSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private JiraService jiraService;

    @BeforeEach
    void setUp() {
        jiraService = new JiraService(
                restTemplate, objectMapper,
                "https://test.atlassian.net",
                "user@test.com", "api-token-123",
                "BACKEND");
    }

    @Test
    void search_returnsMappedIssues() {
        JiraSearchResponse response = new JiraSearchResponse();
        JiraSearchResponse.JiraIssueResult issue = new JiraSearchResponse.JiraIssueResult();
        issue.setKey("BACKEND-123");

        JiraSearchResponse.JiraFields fields = new JiraSearchResponse.JiraFields();
        fields.setSummary("Fix login bug");
        JiraSearchResponse.JiraStatus status = new JiraSearchResponse.JiraStatus();
        status.setName("In Progress");
        fields.setStatus(status);
        JiraSearchResponse.JiraPriority priority = new JiraSearchResponse.JiraPriority();
        priority.setName("High");
        fields.setPriority(priority);
        fields.setDescription("User cannot login with SSO");
        issue.setFields(fields);
        response.setIssues(List.of(issue));

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(JiraSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        List<JiraIssue> result = jiraService.search("login bug", 5);

        assertEquals(1, result.size());
        assertEquals("BACKEND-123", result.get(0).key());
        assertEquals("Fix login bug", result.get(0).summary());
        assertEquals("In Progress", result.get(0).status());
        assertEquals("High", result.get(0).priority());
        assertTrue(result.get(0).url().contains("/browse/BACKEND-123"));
    }

    @Test
    void search_emptyResponse_returnsEmptyList() {
        JiraSearchResponse response = new JiraSearchResponse();
        response.setIssues(null);

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(JiraSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        List<JiraIssue> result = jiraService.search("nothing", 5);
        assertTrue(result.isEmpty());
    }

    @Test
    void search_withApiException_throwsToolExecutionException() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(JiraSearchResponse.class)))
                .thenThrow(new RuntimeException("Jira API timeout"));

        assertThrows(ToolExecutionException.class, () -> jiraService.search("test", 5));
    }

    @Test
    void search_withNullStatusAndPriority_usesDefaults() {
        JiraSearchResponse response = new JiraSearchResponse();
        JiraSearchResponse.JiraIssueResult issue = new JiraSearchResponse.JiraIssueResult();
        issue.setKey("BACKEND-456");

        JiraSearchResponse.JiraFields fields = new JiraSearchResponse.JiraFields();
        fields.setSummary("Test issue");
        fields.setStatus(null);
        fields.setPriority(null);
        fields.setDescription("desc");
        issue.setFields(fields);
        response.setIssues(List.of(issue));

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(JiraSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        List<JiraIssue> result = jiraService.search("test", 5);

        assertEquals("Unknown", result.get(0).status());
        assertEquals("None", result.get(0).priority());
    }

    @Test
    void search_withoutProjectKey_includesNoProjectFilter() {
        JiraService serviceNoProject = new JiraService(
                restTemplate, objectMapper,
                "https://test.atlassian.net",
                "user@test.com", "token", "");

        JiraSearchResponse response = new JiraSearchResponse();
        response.setIssues(List.of());
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(JiraSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        serviceNoProject.search("test", 5);

        verify(restTemplate).exchange(argThat((URI uri) -> {
            String query = uri.toString();
            return !query.contains("project =");
        }), eq(HttpMethod.GET), any(), eq(JiraSearchResponse.class));
    }

    @Test
    void search_withNullDescription_extractsEmptyString() {
        JiraSearchResponse response = new JiraSearchResponse();
        JiraSearchResponse.JiraIssueResult issue = new JiraSearchResponse.JiraIssueResult();
        issue.setKey("BACKEND-789");

        JiraSearchResponse.JiraFields fields = new JiraSearchResponse.JiraFields();
        fields.setSummary("No desc");
        fields.setDescription(null);
        issue.setFields(fields);
        response.setIssues(List.of(issue));

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(JiraSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        List<JiraIssue> result = jiraService.search("test", 5);
        assertEquals("", result.get(0).description());
    }

    @Test
    void search_withJqlInjection_escapesSafely() {
        JiraSearchResponse response = new JiraSearchResponse();
        JiraSearchResponse.JiraIssueResult issue = new JiraSearchResponse.JiraIssueResult();
        issue.setKey("BACKEND-999");
        JiraSearchResponse.JiraFields fields = new JiraSearchResponse.JiraFields();
        fields.setSummary("Test");
        fields.setDescription("desc");
        issue.setFields(fields);
        response.setIssues(List.of(issue));

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(JiraSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        String maliciousQuery = "test\") OR 1=1 OR (\"";
        List<JiraIssue> result = jiraService.search(maliciousQuery, 5);

        assertEquals(1, result.size());
    }

    @Test
    void search_withNullBody_returnsEmpty() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(JiraSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(null));

        List<JiraIssue> result = jiraService.search("test", 5);
        assertTrue(result.isEmpty());
    }

    @Test
    void search_includesProjectAndOrderByInJql() {
        JiraSearchResponse response = new JiraSearchResponse();
        response.setIssues(List.of());
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(JiraSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        jiraService.search("bug", 5);

        verify(restTemplate).exchange(argThat((URI uri) -> {
            String q = uri.toString();
            return q.contains("project") && q.contains("ORDER%20BY") && q.contains("summary") && q.contains("description");
        }), eq(HttpMethod.GET), any(), eq(JiraSearchResponse.class));
    }

    @Test
    void search_withLongQuery_truncatesBeforeJql() {
        JiraSearchResponse response = new JiraSearchResponse();
        response.setIssues(List.of());
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(JiraSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        String veryLongQuery = "test " + "a".repeat(500);
        jiraService.search(veryLongQuery, 5);

        verify(restTemplate).exchange(argThat((URI uri) -> {
            String q = uri.toString();
            return q.length() < 1000;
        }), eq(HttpMethod.GET), any(), eq(JiraSearchResponse.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void search_withAdfDescription_parsesCorrectly() throws Exception {
        Map<String, Object> adf = Map.of(
                "type", "doc",
                "content", List.of(
                        Map.of("type", "paragraph",
                                "content", List.of(
                                        Map.of("type", "text", "text", "Hello World")
                                ))
                )
        );

        JiraSearchResponse response = new JiraSearchResponse();
        JiraSearchResponse.JiraIssueResult issue = new JiraSearchResponse.JiraIssueResult();
        issue.setKey("BACKEND-ADF-1");
        JiraSearchResponse.JiraFields fields = new JiraSearchResponse.JiraFields();
        fields.setSummary("ADF issue");
        fields.setDescription(adf);
        issue.setFields(fields);
        response.setIssues(List.of(issue));

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(JiraSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        when(objectMapper.writeValueAsString(adf)).thenReturn("adf-json");
        when(objectMapper.readValue(eq("adf-json"), eq(Map.class))).thenReturn(adf);

        List<JiraIssue> result = jiraService.search("adf", 5);

        assertEquals(1, result.size());
        assertTrue(result.get(0).description().contains("Hello World"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void search_withInvalidAdfDescription_returnsEmptyDescription() throws Exception {
        Map<String, Object> invalidAdf = Map.of("type", "doc", "invalid", true);

        JiraSearchResponse response = new JiraSearchResponse();
        JiraSearchResponse.JiraIssueResult issue = new JiraSearchResponse.JiraIssueResult();
        issue.setKey("BACKEND-BAD-1");
        JiraSearchResponse.JiraFields fields = new JiraSearchResponse.JiraFields();
        fields.setSummary("Bad ADF");
        fields.setDescription(invalidAdf);
        issue.setFields(fields);
        response.setIssues(List.of(issue));

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(JiraSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        when(objectMapper.writeValueAsString(invalidAdf)).thenReturn("{}");
        when(objectMapper.readValue(eq("{}"), eq(Map.class))).thenThrow(new RuntimeException("Parse error"));

        List<JiraIssue> result = jiraService.search("bad", 5);
        assertEquals("", result.get(0).description());
    }
}
