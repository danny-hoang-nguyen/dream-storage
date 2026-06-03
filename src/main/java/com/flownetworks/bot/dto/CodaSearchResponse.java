package com.flownetworks.bot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// Response từ GET /docs
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodaSearchResponse {
    private List<CodaDocResult> items;

    public List<CodaDocResult> getItems() { return items; }
    public void setItems(List<CodaDocResult> items) { this.items = items; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodaDocResult {
        private String id;
        private String name;
        private String browserLink;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBrowserLink() { return browserLink; }
        public void setBrowserLink(String browserLink) { this.browserLink = browserLink; }
    }
}
