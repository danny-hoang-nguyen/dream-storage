package com.flownetworks.bot.service;

import com.flownetworks.bot.dto.CodaDoc;
import com.flownetworks.bot.dto.CodaPageSearchResponse;
import com.flownetworks.bot.dto.CodaSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class CodaService {

    private static final Logger log = LoggerFactory.getLogger(CodaService.class);
    private static final String CODA_API = "https://coda.io/apis/v1";
    private static final int PAGE_FETCH_LIMIT = 50;
    private static final int MAX_PAGE_REQUESTS = 5; // tối đa 250 pages mỗi doc

    private final RestTemplate restTemplate;
    private final HttpHeaders headers;
    private final String docId; // Nếu để trống thì search toàn bộ workspace

    public CodaService(
            RestTemplate externalApiRestTemplate,
            @Value("${coda.api-token}") String apiToken,
            @Value("${coda.doc-id:}") String docId) {

        this.restTemplate = externalApiRestTemplate;
        this.docId = docId;

        this.headers = new HttpHeaders();
        this.headers.set("Authorization", "Bearer " + apiToken);
        this.headers.set("Accept", "application/json");
    }

    /**
     * Search docs theo keyword. Nếu docId được cấu hình, search page trong doc đó.
     * Nếu không, search tất cả docs trong workspace theo tên.
     */
    public List<CodaDoc> search(String keyword, int maxResults) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        if (docId != null && !docId.isBlank()) {
            return searchPagesInDoc(docId, keyword, maxResults);
        } else {
            return searchDocs(keyword, maxResults);
        }
    }

    /**
     * Search docs theo tên trong toàn workspace
     */
    private List<CodaDoc> searchDocs(String keyword, int maxResults) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = CODA_API + "/docs?query=" + encoded + "&limit=" + maxResults;
            log.debug("Coda searchDocs url={}", url);

            ResponseEntity<CodaSearchResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), CodaSearchResponse.class
            );

            if (response.getBody() == null || response.getBody().getItems() == null) {
                log.debug("Coda searchDocs: empty response for keyword='{}'", keyword);
                return Collections.emptyList();
            }

            log.debug("Coda searchDocs: {} docs returned for keyword='{}'",
                response.getBody().getItems().size(), keyword);

            List<CodaDoc> results = new ArrayList<>();
            for (CodaSearchResponse.CodaDocResult item : response.getBody().getItems()) {
                // Lấy thêm nội dung tóm tắt từ pages của doc này
                List<CodaDoc> pages = searchPagesInDoc(item.getId(), keyword, 2);
                if (!pages.isEmpty()) {
                    results.addAll(pages);
                } else {
                    results.add(new CodaDoc(item.getName(), "", item.getBrowserLink(), item.getId()));
                }
                if (results.size() >= maxResults) break;
            }

            log.debug("Coda searchDocs: {} results after page expansion (max={})", results.size(), maxResults);
            return results.subList(0, Math.min(results.size(), maxResults));

        } catch (Exception e) {
            log.warn("Coda doc search failed for '{}': {}", keyword, e.getMessage());
            throw new ToolExecutionException("Coda API search thất bại", e);
        }
    }

    /**
     * Search pages bên trong một doc cụ thể.
     * Coda Pages API không hỗ trợ full-text query, nên ta phân trang qua các pages
     * rồi match theo từng token trong keyword (vì keyword từ Claude thường là cả câu).
     */
    private List<CodaDoc> searchPagesInDoc(String targetDocId, String keyword, int maxResults) {
        List<CodaDoc> matched = new ArrayList<>();
        List<String> tokens = tokenize(keyword);
        List<Pattern> tokenPatterns = compileTokenPatterns(tokens);
        try {
            String url = CODA_API + "/docs/" + targetDocId + "/pages?limit=" + PAGE_FETCH_LIMIT;
            int totalPages = 0;

            for (int req = 0; req < MAX_PAGE_REQUESTS && url != null && matched.size() < maxResults; req++) {
                log.debug("Coda searchPagesInDoc docId={} req#{} url={}", targetDocId, req, url);

                ResponseEntity<CodaPageSearchResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), CodaPageSearchResponse.class
                );

                CodaPageSearchResponse body = response.getBody();
                if (body == null || body.getItems() == null) {
                    log.debug("Coda searchPagesInDoc: empty response for docId={} (req#{})", targetDocId, req);
                    break;
                }

                totalPages += body.getItems().size();

                body.getItems().stream()
                    .filter(page -> matchesTokens(page.getName(), page.getSubtitle(), tokenPatterns))
                    .limit(maxResults - matched.size())
                    .map(page -> new CodaDoc(
                        page.getName(),
                        page.getSubtitle() != null ? page.getSubtitle() : "",
                        page.getBrowserLink(),
                        targetDocId
                    ))
                    .forEach(matched::add);

                url = body.getNextPageLink(); // absolute URL hoặc null
            }

            log.debug("Coda pages in docId={}: scanned={}, matched keyword='{}': {}",
                targetDocId, totalPages, keyword, matched.size());
            return matched;

        } catch (Exception e) {
            log.warn("Coda page search failed in doc '{}' for '{}': {}", targetDocId, keyword, e.getMessage());
            throw new ToolExecutionException("Coda API page search thất bại", e);
        }
    }

    /**
     * Tách keyword thành các token có nghĩa (>= 3 ký tự, lowercase).
     */
    private List<String> tokenize(String keyword) {
        if (keyword == null || keyword.isBlank()) return Collections.emptyList();
        return Arrays.stream(keyword.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
            .filter(t -> t.length() >= 3)
            .distinct()
            .toList();
    }

    /**
     * Match nếu name/subtitle chứa ít nhất một token. Nếu không có token hợp lệ
     * (keyword toàn từ ngắn) thì coi như match để không bỏ sót.
     */
    private boolean matchesTokens(String name, String subtitle, List<Pattern> tokenPatterns) {
        if (tokenPatterns.isEmpty()) return true;
        String haystack = (name != null ? name : "") + " " + (subtitle != null ? subtitle : "");
        return tokenPatterns.stream().anyMatch(pattern -> pattern.matcher(haystack).find());
    }

    private List<Pattern> compileTokenPatterns(List<String> tokens) {
        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }
        return tokens.stream()
            .map(token -> Pattern.compile("\\b" + Pattern.quote(token) + "\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
            .toList();
    }
}
