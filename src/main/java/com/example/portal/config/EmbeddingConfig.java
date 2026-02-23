package com.example.portal.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Конфигурация EmbeddingModel по app.embedding-provider.
 * OLLAMA (768), OPENAI (1536), CORPORATE (1536), CUSTOM (формат inputs/model/encoding_format).
 */
@Configuration
public class EmbeddingConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.embedding-provider", havingValue = "OLLAMA", matchIfMissing = true)
    public EmbeddingModel primaryEmbeddingModelOllama(
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel ollamaEmbeddingModel,
            @Value("${app.debug.embedding-curl:false}") boolean debugCurl,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text}") String ollamaModel,
            @Value("${OLLAMA_API_KEY:}") String apiKeyEnvVar
    ) {
        if (debugCurl) {
            return new EmbeddingModelLoggingDecorator(ollamaEmbeddingModel, ollamaBaseUrl, ollamaModel, "OLLAMA_API_KEY");
        }
        return ollamaEmbeddingModel;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.embedding-provider", havingValue = "CUSTOM")
    public EmbeddingModel primaryEmbeddingModelCustom(
            @Value("${app.custom-embedding.base-url:}") String baseUrl,
            @Value("${app.custom-embedding.model:Salesforce/SFR-Embedding-Code-400M_R}") String model,
            @Value("${app.custom-embedding.api-key:}") String apiKey
    ) {
        return new CustomEmbeddingModel(baseUrl, model, apiKey);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.embedding-provider", havingValue = "OPENAI")
    public EmbeddingModel primaryEmbeddingModelOpenAi(@Qualifier("openAiEmbeddingModel") EmbeddingModel openAiEmbeddingModel) {
        return openAiEmbeddingModel;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.embedding-provider", havingValue = "CORPORATE")
    public EmbeddingModel primaryEmbeddingModelCorporate(@Qualifier("corporateEmbeddingModel") EmbeddingModel corporateEmbeddingModel) {
        return corporateEmbeddingModel;
    }
}
