package com.example.portal.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Конфигурация для создания ChatClient для различных LLM провайдеров.
 * Выбор провайдера осуществляется через app.llm-provider в application.yml
 */
@Configuration
@Slf4j
public class AiConfig {

    /**
     * ChatModel по умолчанию для ChatClientAutoConfiguration.
     * При наличии Ollama и OpenAI создаётся 2 ChatModel — помечаем OpenAI как @Primary.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.llm-provider", havingValue = "OPENAI", matchIfMissing = true)
    public ChatModel primaryChatModel(@Qualifier("openAiChatModel") ChatModel openAiChatModel) {
        return openAiChatModel;
    }
    
    /**
     * ChatClient для OpenAI (используется по умолчанию или когда app.llm-provider=OPENAI).
     * Spring AI автоматически создает ChatModel из конфигурации spring.ai.openai
     */
    @Bean(name = "chatClient")
    @Primary
    @ConditionalOnProperty(name = "app.llm-provider", havingValue = "OPENAI", matchIfMissing = true)
    public ChatClient openAiChatClient(ChatClient.Builder builder) {
        log.info("Configuring ChatClient for OpenAI");
        return builder.build();
    }
}

