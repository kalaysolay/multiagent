package com.example.portal.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * Конфигурация для создания ChatClient для различных LLM провайдеров.
 * Выбор провайдера осуществляется через app.llm-provider в application.yml
 */
@Configuration
@Slf4j
public class AiConfig {
    
    /**
     * ChatClient для OpenAI (используется по умолчанию или когда app.llm-provider=OPENAI).
     * Spring AI автоматически создает ChatModel из конфигурации spring.ai.openai
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.llm-provider", havingValue = "OPENAI", matchIfMissing = true)
    public ChatClient chatClient(ChatClient.Builder builder) {
        log.info("Configuring ChatClient for OpenAI");
        return builder.build();
    }
    
    /**
     * ChatModel для DeepSeek.
     * DeepSeek использует OpenAI-совместимый API, поэтому используем OpenAiChatModel
     * с настройкой базового URL на api.deepseek.com
     */
    @Bean
    @ConditionalOnProperty(name = "app.llm-provider", havingValue = "DEEPSEEK")
    @Qualifier("deepSeekChatModel")
    public OpenAiChatModel deepSeekChatModel(
            @Value("${app.deepseek.api-key:}") String apiKey,
            @Value("${app.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${app.deepseek.model:deepseek-chat}") String model
    ) {
        log.info("Configuring DeepSeek ChatModel with baseUrl: {}, model: {}", baseUrl, model);
        
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("app.deepseek.api-key must be set when using DeepSeek LLM provider");
        }
        
        // Создаем OpenAiApi с правильными параметрами
        OpenAiApi api = new OpenAiApi(baseUrl, apiKey, RestClient.builder(), 
                org.springframework.web.reactive.function.client.WebClient.builder(), null);
        
        return new OpenAiChatModel(api, OpenAiChatOptions.builder()
                .model(model)
                .temperature(1.0)
                .build());
    }
    
    /**
     * ChatClient для DeepSeek (используется когда app.llm-provider=DEEPSEEK).
     * Использует настроенный DeepSeek ChatModel.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.llm-provider", havingValue = "DEEPSEEK")
    public ChatClient chatClient(
            @Qualifier("deepSeekChatModel") OpenAiChatModel chatModel
    ) {
        log.info("Configuring ChatClient for DeepSeek");
        return ChatClient.builder(chatModel).build();
    }
}

