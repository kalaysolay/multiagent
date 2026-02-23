package com.example.portal.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Конфигурация для корпоративного API (OpenAI-совместимый).
 * Активируется при app.llm-provider=CORPORATE или app.embedding-provider=CORPORATE.
 */
@Configuration
@Slf4j
public class CorporateApiConfig {

    @Bean
    @Qualifier("corporateChatModel")
    @ConditionalOnProperty(name = "app.llm-provider", havingValue = "CORPORATE")
    public ChatModel corporateChatModel(
            @Value("${app.corporate.api-key:}") String apiKey,
            @Value("${app.corporate.base-url:}") String baseUrl,
            @Value("${app.corporate.chat-model:gpt-4}") String model
    ) {
        if (apiKey == null || apiKey.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "app.corporate.api-key and app.corporate.base-url must be set when using CORPORATE LLM provider");
        }
        log.info("Configuring Corporate ChatModel: baseUrl={}, model={}", baseUrl, model);
        OpenAiApi api = new OpenAiApi(baseUrl, apiKey, RestClient.builder(), WebClient.builder(), null);
        return new OpenAiChatModel(api, OpenAiChatOptions.builder()
                .model(model)
                .temperature(1.0)
                .build());
    }

    @Bean(name = "chatClient")
    @Primary
    @ConditionalOnProperty(name = "app.llm-provider", havingValue = "CORPORATE")
    public ChatClient corporateChatClient(@Qualifier("corporateChatModel") ChatModel chatModel) {
        log.info("Configuring ChatClient for CORPORATE LLM");
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    @Qualifier("corporateEmbeddingModel")
    @ConditionalOnProperty(name = "app.embedding-provider", havingValue = "CORPORATE")
    public EmbeddingModel corporateEmbeddingModel(
            @Value("${app.corporate.api-key:}") String apiKey,
            @Value("${app.corporate.base-url:}") String baseUrl,
            @Value("${app.corporate.embedding-model:text-embedding-3-small}") String model
    ) {
        if (apiKey == null || apiKey.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "app.corporate.api-key and app.corporate.base-url must be set when using CORPORATE embedding provider");
        }
        log.info("Configuring Corporate EmbeddingModel: baseUrl={}, model={}", baseUrl, model);
        OpenAiApi api = new OpenAiApi(baseUrl, apiKey, RestClient.builder(), WebClient.builder(), null);
        return new OpenAiEmbeddingModel(api, MetadataMode.NONE,
                OpenAiEmbeddingOptions.builder().model(model).build());
    }
}
