package com.example.workflow.confluence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Клиент к Confluence REST API: GET /rest/api/content/{id}?expand=...
 * Использует app.confluence.base-url, login и password; аутентификация Basic (Authorization: Basic &lt;base64(login:password)&gt;).
 * Создаётся бином в ConfluenceApiConfig при заданном app.confluence.base-url.
 */
public class ConfluenceApiClient {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceApiClient.class);
    private static final String DEFAULT_EXPAND = "body.storage";

    private final String baseUrl;
    private final WebClient webClient;

    public ConfluenceApiClient(String baseUrl, String login, String password, WebClient.Builder webClientBuilder) {
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "";
        String url = this.baseUrl.isEmpty() ? "http://localhost" : this.baseUrl;
        String credentials = (login != null ? login : "") + ":" + (password != null ? password : "");
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.webClient = webClientBuilder
                .baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                .build();
    }

    /**
     * Получить страницу по id. expandParams — строка для query-параметра expand (например "body.storage,version");
     * если пусто — используется "body.storage". Запрос всегда с ?expand=...
     */
    public Optional<PageContent> getPage(String id, String expandParams) {
        if (baseUrl.isEmpty()) {
            log.debug("Confluence base-url not set, skipping getPage");
            return Optional.empty();
        }
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String expand = (expandParams != null && !expandParams.isBlank()) ? expandParams.trim() : DEFAULT_EXPAND;
        try {
            PageContent page = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/content/{id}")
                            .queryParam("expand", expand)
                            .build(id.trim()))
                    .retrieve()
                    .bodyToMono(PageContent.class)
                    .block();
            return Optional.ofNullable(page);
        } catch (WebClientResponseException e) {
            log.warn("Confluence API error for page {}: {} {}", id, e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching Confluence page {}", id, e);
            return Optional.empty();
        }
    }
}
