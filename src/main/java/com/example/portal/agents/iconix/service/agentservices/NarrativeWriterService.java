package com.example.portal.agents.iconix.service.agentservices;

import com.example.portal.prompt.service.PromptService;
import com.example.portal.shared.utils.PromptUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Сервис для генерации пользовательского нарратива для ICONIX-моделирования.
 * <p>
 * Промпт загружается из справочника (PromptService) по коду:
 * - "narrative_writer" — промпт для формирования нарратива (содержит %s)
 */
@Slf4j
@Service
public class NarrativeWriterService {

    private final ChatClient chat;
    private final PromptService promptService;

    @Autowired
    public NarrativeWriterService(ChatClient.Builder builder, PromptService promptService) {
        this.chat = builder.build();
        this.promptService = promptService;
    }

    /**
     * Генерирует подробный пользовательский нарратив на основе описания задачи,
     * бизнес-цели и RAG-контекста.
     */
    public String composeNarrative(String taskDescription, String goal, String ragContext) {
        // Загружаем промпт из БД (или кэша)
        String promptTemplate = promptService.getByCode("narrative_writer");
        log.info("Template: {} ", promptTemplate);
        String prompt = String.format(promptTemplate,
                safe(taskDescription), safe(goal), normalizeContext(ragContext));
        log.info("prompt: {} ", prompt);
        return chat.prompt()
                .user(PromptUtils.stEscape(prompt))
                .options(OpenAiChatOptions.builder()
                        .temperature(1.0)
                        .build())
                .call()
                .content();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeContext(String ragContext) {
        return (ragContext == null || ragContext.isBlank()) ? "нет" : ragContext;
    }
}
