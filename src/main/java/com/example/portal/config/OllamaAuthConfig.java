package com.example.portal.config;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Добавляет Authorization: Bearer для Ollama API, если задан spring.ai.ollama.api-key.
 * Используется при вызове удалённого Ollama-совместимого API, требующего аутентификации.
 * Spring AI Ollama 1.0.0-M6 не поддерживает api-key в нативных настройках.
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.ollama.api-key")
public class OllamaAuthConfig {

    @Bean
    @ConditionalOnMissingBean(OllamaApi.class)
    public OllamaApi ollamaApi(
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${spring.ai.ollama.api-key}") String apiKey) {

        RestClient.Builder restBuilder = RestClient.builder()
                .defaultHeader("Authorization", "Bearer " + apiKey);
        WebClient.Builder webBuilder = WebClient.builder()
                .defaultHeader("Authorization", "Bearer " + apiKey);

        return new OllamaApi(baseUrl, restBuilder, webBuilder);
    }
}
