package com.example.workflow.confluence;

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
 * Unit-тесты для ConfluenceApiClient: getPage (пустой baseUrl/id, успешный ответ, ошибка API).
 */
class ConfluenceApiClientTest {

    @Test
    @DisplayName("getPage returns empty when baseUrl is empty")
    void getPage_returnsEmpty_whenBaseUrlEmpty() {
        WebClient.Builder builder = WebClient.builder().baseUrl("http://localhost");
        ConfluenceApiClient client = new ConfluenceApiClient("", "user", "pass", builder);

        Optional<PageContent> result = client.getPage("123", "body.storage,version");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getPage returns empty when id is blank")
    void getPage_returnsEmpty_whenIdBlank() {
        ExchangeFunction noCall = request -> Mono.error(new AssertionError("should not call"));
        WebClient.Builder builder = WebClient.builder().baseUrl("http://confluence").exchangeFunction(noCall);
        ConfluenceApiClient client = new ConfluenceApiClient("http://confluence", "user", "pass", builder);

        assertThat(client.getPage(null, "body.storage")).isEmpty();
        assertThat(client.getPage("", "body.storage")).isEmpty();
    }

    @Test
    @DisplayName("getPage returns PageContent on successful API response")
    void getPage_returnsPageContent_onSuccess() {
        String json = "{\"id\":\"123\",\"type\":\"page\",\"title\":\"Test Page\",\"version\":{\"number\":1},\"body\":{\"storage\":{\"value\":\"<p>Content</p>\"}}}";
        DataBuffer buffer = new DefaultDataBufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        ExchangeFunction stub = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(Flux.just(buffer))
                        .build());
        WebClient.Builder builder = WebClient.builder()
                .baseUrl("http://confluence")
                .exchangeFunction(stub);
        ConfluenceApiClient client = new ConfluenceApiClient("http://confluence", "user", "pass", builder);

        Optional<PageContent> result = client.getPage("123", "body.storage,version");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("123");
        assertThat(result.get().getTitle()).isEqualTo("Test Page");
        assertThat(result.get().getBody()).isNotNull();
        assertThat(result.get().getBody().getStorage()).isNotNull();
        assertThat(result.get().getBody().getStorage().getValue()).contains("Content");
    }

    @Test
    @DisplayName("getPage returns empty on API error")
    void getPage_returnsEmpty_onApiError() {
        ExchangeFunction stub = request -> Mono.just(
                ClientResponse.create(HttpStatus.NOT_FOUND).build());
        WebClient.Builder builder = WebClient.builder()
                .baseUrl("http://confluence")
                .exchangeFunction(stub);
        ConfluenceApiClient client = new ConfluenceApiClient("http://confluence", "user", "pass", builder);

        Optional<PageContent> result = client.getPage("999", "body.storage");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getPage with null or empty expandParams uses default body.storage and returns result")
    void getPage_withEmptyExpand_usesDefaultAndReturnsResult() {
        String json = "{\"id\":\"456\",\"type\":\"page\",\"title\":\"Other\",\"body\":{\"storage\":{\"value\":\"<p>Text</p>\"}}}";
        DataBuffer buffer = new DefaultDataBufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        ExchangeFunction stub = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(Flux.just(buffer))
                        .build());
        WebClient.Builder builder = WebClient.builder().baseUrl("http://confluence").exchangeFunction(stub);
        ConfluenceApiClient client = new ConfluenceApiClient("http://confluence", "user", "pass", builder);

        Optional<PageContent> resultNull = client.getPage("456", null);
        Optional<PageContent> resultEmpty = client.getPage("456", "");

        assertThat(resultNull).isPresent();
        assertThat(resultNull.get().getId()).isEqualTo("456");
        assertThat(resultEmpty).isPresent();
        assertThat(resultEmpty.get().getTitle()).isEqualTo("Other");
    }
}
