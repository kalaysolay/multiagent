package com.example.portal.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Конфигурация EmbeddingModel.
 * При наличии Ollama и OpenAI создаётся 2 EmbeddingModel — помечаем один как @Primary
 * для PgVectorStoreAutoConfiguration и других компонентов.
 */
@Configuration
public class EmbeddingConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.vector-store-provider", havingValue = "LOCAL", matchIfMissing = true)
    public EmbeddingModel primaryEmbeddingModelLocal(@Qualifier("ollamaEmbeddingModel") EmbeddingModel ollamaEmbeddingModel) {
        return ollamaEmbeddingModel;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.vector-store-provider", havingValue = "OPENAI")
    public EmbeddingModel primaryEmbeddingModelOpenAi(@Qualifier("openAiEmbeddingModel") EmbeddingModel openAiEmbeddingModel) {
        return openAiEmbeddingModel;
    }
}
