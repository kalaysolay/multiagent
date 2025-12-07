package com.example.portal.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Реализация LLM сервиса для OpenAI ChatGPT.
 * Использует ChatClient, который автоматически настроен в AiConfig в зависимости от app.llm-provider.
 */
@Service
@ConditionalOnProperty(name = "app.llm-provider", havingValue = "OPENAI", matchIfMissing = true)
@Slf4j
public class OpenAiLlmService implements LlmService {
    
    private final ChatClient chatClient;
    
    @Autowired
    public OpenAiLlmService(ChatClient chatClient) {
        this.chatClient = chatClient;
        log.info("Initialized OpenAI LLM Service");
    }
    
    @Override
    public String generate(String prompt) {
        return generate(prompt, 1.0);
    }
    
    @Override
    public String generate(String prompt, Double temperature) {
        return chatClient.prompt()
                .user(prompt)
                .options(OpenAiChatOptions.builder()
                        .temperature(temperature != null ? temperature : 1.0)
                        .build())
                .call()
                .content();
    }
}

