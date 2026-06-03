package com.flownetworks.bot.service;

import com.flownetworks.bot.dto.CodaDoc;
import com.flownetworks.bot.dto.CodaPageSearchResponse;
import com.flownetworks.bot.dto.CodaSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodaServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private CodaService codaService;

    @BeforeEach
    void setUp() {
        codaService = new CodaService(restTemplate, "test-token", "");
    }

    @Test
    void search_withDocId_delegatesToSearchPagesInDoc() {
        CodaService serviceWithDoc = new CodaService(restTemplate, "test-token", "doc-123");

        CodaPageSearchResponse body = new CodaPageSearchResponse();
        CodaPageSearchResponse.CodaPageResult page = new CodaPageSearchResponse.CodaPageResult();
        page.setId("page-1");
        page.setName("API Docs");
        page.setSubtitle("REST API guide");
        page.setBrowserLink("https://coda.io/page-1");
        body.setItems(List.of(page));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CodaPageSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(body));

        List<CodaDoc> result = serviceWithDoc.search("api", 5);

        assertEquals(1, result.size());
        assertEquals("API Docs", result.get(0).title());
        assertEquals("REST API guide", result.get(0).excerpt());
        verify(restTemplate).exchange(matches(".*/docs/doc-123/pages.*"), any(HttpMethod.class), any(HttpEntity.class), eq(CodaPageSearchResponse.class));
    }

    @Test
    void search_withoutDocId_searchesDocs() {
        CodaSearchResponse body = new CodaSearchResponse();
        CodaSearchResponse.CodaDocResult doc = new CodaSearchResponse.CodaDocResult();
        doc.setId("doc-1");
        doc.setName("Backend Docs");
        doc.setBrowserLink("https://coda.io/doc-1");
        body.setItems(List.of(doc));

        CodaPageSearchResponse emptyPages = new CodaPageSearchResponse();
        emptyPages.setItems(List.of());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CodaSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(body));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CodaPageSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(emptyPages));

        List<CodaDoc> result = codaService.search("backend", 5);

        assertEquals(1, result.size());
        assertEquals("Backend Docs", result.get(0).title());
        verify(restTemplate).exchange(matches(".*/docs\\?query=.*"), eq(HttpMethod.GET), any(HttpEntity.class), eq(CodaSearchResponse.class));
    }

    @Test
    void search_withEmptyResponse_returnsEmptyList() {
        CodaSearchResponse body = new CodaSearchResponse();
        body.setItems(null);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CodaSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(body));

        List<CodaDoc> result = codaService.search("nothing", 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void searchPagesInDoc_pagination_handlesMultiplePages() {
        CodaService serviceWithDoc = new CodaService(restTemplate, "test-token", "doc-1");

        CodaPageSearchResponse page1 = new CodaPageSearchResponse();
        CodaPageSearchResponse.CodaPageResult p1 = new CodaPageSearchResponse.CodaPageResult();
        p1.setId("p1"); p1.setName("API v1"); p1.setBrowserLink("https://coda.io/p1");
        page1.setItems(List.of(p1));
        page1.setNextPageLink("https://coda.io/apis/v1/docs/doc-1/pages?pageToken=2");

        CodaPageSearchResponse page2 = new CodaPageSearchResponse();
        CodaPageSearchResponse.CodaPageResult p2 = new CodaPageSearchResponse.CodaPageResult();
        p2.setId("p2"); p2.setName("API v2"); p2.setBrowserLink("https://coda.io/p2");
        page2.setItems(List.of(p2));
        page2.setNextPageLink(null);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CodaPageSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(page1), ResponseEntity.ok(page2));

        List<CodaDoc> result = serviceWithDoc.search("API", 5);

        assertEquals(2, result.size());
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CodaPageSearchResponse.class));
    }

    @Test
    void search_withApiException_throwsToolExecutionException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CodaSearchResponse.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThrows(ToolExecutionException.class, () -> codaService.search("test", 5));
    }

    @Test
    void search_withNullKeyword_returnsEmpty() {
        assertTrue(codaService.search(null, 5).isEmpty());
    }

    @Test
    void search_withBlankKeyword_returnsEmpty() {
        assertTrue(codaService.search("   ", 5).isEmpty());
    }

    @Test
    void searchPagesInDoc_withEmptyResult_returnsEmpty() {
        CodaService serviceWithDoc = new CodaService(restTemplate, "token", "doc-1");

        CodaPageSearchResponse body = new CodaPageSearchResponse();
        body.setItems(null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CodaPageSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(body));

        List<CodaDoc> result = serviceWithDoc.search("test", 5);
        assertTrue(result.isEmpty());
    }

    @Test
    void searchPagesInDoc_maxPageRequests_respected() {
        CodaService serviceWithDoc = new CodaService(restTemplate, "token", "doc-1");

        CodaPageSearchResponse page = new CodaPageSearchResponse();
        page.setItems(List.of());
        page.setNextPageLink("https://coda.io/next");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CodaPageSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(page));

        List<CodaDoc> result = serviceWithDoc.search("test", 5);
        assertTrue(result.isEmpty());
        verify(restTemplate, atMost(5)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CodaPageSearchResponse.class));
    }

    @Test
    void search_withMatchingPages_expandsResults() {
        CodaService serviceWithDoc = new CodaService(restTemplate, "token", "doc-1");

        CodaPageSearchResponse body = new CodaPageSearchResponse();
        CodaPageSearchResponse.CodaPageResult page = new CodaPageSearchResponse.CodaPageResult();
        page.setId("p1"); page.setName("Matching Title"); page.setSubtitle("Relevant content");
        page.setBrowserLink("https://coda.io/p1");
        body.setItems(List.of(page));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CodaPageSearchResponse.class)))
                .thenReturn(ResponseEntity.ok(body));

        List<CodaDoc> result = serviceWithDoc.search("Matching", 5);

        assertEquals(1, result.size());
        assertEquals("Matching Title", result.get(0).title());
    }
}
