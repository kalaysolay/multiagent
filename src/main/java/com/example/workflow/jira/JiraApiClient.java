package com.example.workflow.jira;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Optional;

/**
 * Клиент к Jira REST API: GET /rest/api/2/issue/{issueIdOrKey} с Bearer-токеном.
 * URL: {app.jira.base-path}/rest/api/2/issue/{issueIdOrKey}.
 */
@Slf4j
@Service
public class JiraApiClient {

    private final String basePath;
    private final WebClient webClient;

    public JiraApiClient(
            @Value("${app.jira.base-path:}") String basePath,
            @Value("${app.jira.bearer-token:}") String bearerToken,
            WebClient.Builder webClientBuilder) {
        this.basePath = basePath != null ? basePath.trim() : "";
        String baseUrl = this.basePath.isEmpty() ? "http://localhost" : this.basePath;
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (bearerToken != null ? bearerToken : ""))
                .build();
    }

    /**
     * Запрашивает задачу по ключу или id. Если base-path не задан или запрос неуспешен — возвращает empty.
     */
    public Optional<JiraIssue> getIssue(String issueIdOrKey) {
        if (basePath.isEmpty()) {
            log.debug("Jira base-path not set, skipping getIssue");
            return Optional.empty();
        }
        if (issueIdOrKey == null || issueIdOrKey.isBlank()) {
            return Optional.empty();
        }
        try {
            JiraIssue issue = webClient
                    .get()
                    .uri("/rest/api/2/issue/{issueIdOrKey}", issueIdOrKey.trim())
                    .retrieve()
                    .bodyToMono(JiraIssue.class)
                    .block();
            return Optional.ofNullable(issue);
        } catch (WebClientResponseException e) {
            log.warn("Jira API error for issue {}: {} {}", issueIdOrKey, e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching Jira issue {}", issueIdOrKey, e);
            return Optional.empty();
        }
    }

    /**
     * Поиск задач по JQL. GET /rest/api/2/search?jql=...&maxResults=...&startAt=0.
     * При пустом basePath или jql возвращает empty.
     */
    public Optional<SearchResult> searchByJQL(String jql, int limit) {
        if (basePath.isEmpty()) {
            log.debug("Jira base-path not set, skipping searchByJQL");
            return Optional.empty();
        }
        if (jql == null || jql.isBlank()) {
            return Optional.empty();
        }
        int maxResults = limit <= 0 ? 50 : limit;
        try {
            SearchResult result = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/2/search")
                            .queryParam("jql", jql.trim())
                            .queryParam("maxResults", maxResults)
                            .queryParam("startAt", 0)
                            .build())
                    .retrieve()
                    .bodyToMono(SearchResult.class)
                    .block();
            return Optional.ofNullable(result);
        } catch (WebClientResponseException e) {
            log.warn("Jira API error for search: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error searching Jira by JQL", e);
            return Optional.empty();
        }
    }
}
