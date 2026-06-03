package com.flownetworks.bot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// Response từ Jira REST API v3 /rest/api/3/search/jql (enhanced search)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraSearchResponse {
    private List<JiraIssueResult> issues;

    public List<JiraIssueResult> getIssues() { return issues; }
    public void setIssues(List<JiraIssueResult> issues) { this.issues = issues; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraIssueResult {
        private String id;
        private String key;
        private JiraFields fields;
        private String self; // API URL, dùng để build browser URL

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public JiraFields getFields() { return fields; }
        public void setFields(JiraFields fields) { this.fields = fields; }
        public String getSelf() { return self; }
        public void setSelf(String self) { this.self = self; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraFields {
        private String summary;
        private JiraStatus status;
        private JiraPriority priority;
        private Object description; // Có thể là String hoặc ADF object

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public JiraStatus getStatus() { return status; }
        public void setStatus(JiraStatus status) { this.status = status; }
        public JiraPriority getPriority() { return priority; }
        public void setPriority(JiraPriority priority) { this.priority = priority; }
        public Object getDescription() { return description; }
        public void setDescription(Object description) { this.description = description; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraStatus {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraPriority {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
