package com.flownetworks.bot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// Response từ GET /docs/{docId}/pages khi search nội dung
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodaPageSearchResponse {
    private List<CodaPageResult> items;
    private String nextPageLink; // URL trang kế tiếp (Coda pagination)

    public List<CodaPageResult> getItems() { return items; }
    public void setItems(List<CodaPageResult> items) { this.items = items; }
    public String getNextPageLink() { return nextPageLink; }
    public void setNextPageLink(String nextPageLink) { this.nextPageLink = nextPageLink; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodaPageResult {
        private String id;
        private String name;
        private String browserLink;
        private String subtitle;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBrowserLink() { return browserLink; }
        public void setBrowserLink(String browserLink) { this.browserLink = browserLink; }
        public String getSubtitle() { return subtitle; }
        public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    }
}
