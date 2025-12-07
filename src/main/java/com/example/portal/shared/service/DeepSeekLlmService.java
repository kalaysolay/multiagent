package com.example.portal.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Реализация LLM сервиса для DeepSeek.
 * DeepSeek использует OpenAI-совместимый API, поэтому можно использовать те же интерфейсы.
 * Использует ChatClient, который автоматически настроен в AiConfig для DeepSeek.
 */
@Service
@ConditionalOnProperty(name = "app.llm-provider", havingValue = "DEEPSEEK")
@Slf4j
public class DeepSeekLlmService implements LlmService {
    
    private final ChatClient chatClient;
    
    @Autowired
    public DeepSeekLlmService(ChatClient chatClient) {
        this.chatClient = chatClient;
        log.info("Initialized DeepSeek LLM Service");
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

