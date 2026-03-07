package com.example.workflow.jira;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты для JiraApiClient: searchByJQL (guard clauses и успешный ответ через ExchangeFunction).
 */
class JiraApiClientTest {

    @Test
    @DisplayName("searchByJQL returns empty when basePath is empty")
    void searchByJQL_returnsEmpty_whenBasePathEmpty() {
        WebClient.Builder builder = WebClient.builder().baseUrl("http://localhost");
        JiraApiClient client = new JiraApiClient("", "token", builder);

        Optional<SearchResult> result = client.searchByJQL("project = X", 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("searchByJQL returns empty when jql is blank")
    void searchByJQL_returnsEmpty_whenJqlBlank() {
        ExchangeFunction noCall = request -> Mono.error(new AssertionError("should not call"));
        WebClient.Builder builder = WebClient.builder().baseUrl("http://localhost").exchangeFunction(noCall);
        JiraApiClient client = new JiraApiClient("http://jira", "token", builder);

        assertThat(client.searchByJQL(null, 10)).isEmpty();
        assertThat(client.searchByJQL("", 10)).isEmpty();
        assertThat(client.searchByJQL("   ", 10)).isEmpty();
    }

    @Test
    @DisplayName("searchByJQL returns SearchResult on successful API response")
    void searchByJQL_returnsSearchResult_onSuccess() {
        String json = "{\"issues\":[{\"id\":\"1\",\"key\":\"PROJ-1\",\"fields\":{\"summary\":\"Task 1\"}}],\"startAt\":0,\"maxResults\":10,\"total\":1}";
        DataBuffer buffer = new DefaultDataBufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        ExchangeFunction stub = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(Flux.just(buffer))
                        .build());
        WebClient.Builder builder = WebClient.builder()
                .baseUrl("http://jira")
                .exchangeFunction(stub);
        JiraApiClient client = new JiraApiClient("http://jira", "token", builder);

        Optional<SearchResult> result = client.searchByJQL("project = PROJ", 10);

        assertThat(result).isPresent();
        assertThat(result.get().getTotal()).isEqualTo(1);
        assertThat(result.get().getIssues()).hasSize(1);
        assertThat(result.get().getIssues().get(0).getKey()).isEqualTo("PROJ-1");
    }

    @Test
    @DisplayName("searchByJQL returns empty on API error 4xx")
    void searchByJQL_returnsEmpty_onApiError() {
        ExchangeFunction stub = request -> Mono.just(
                ClientResponse.create(HttpStatus.UNAUTHORIZED).build());
        WebClient.Builder builder = WebClient.builder()
                .baseUrl("http://jira")
                .exchangeFunction(stub);
        JiraApiClient client = new JiraApiClient("http://jira", "token", builder);

        Optional<SearchResult> result = client.searchByJQL("project = X", 10);

        assertThat(result).isEmpty();
    }
}
