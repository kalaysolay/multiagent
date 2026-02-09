package com.example.portal.agents.iconix.service.agentservices;

import com.example.portal.prompt.service.PromptService;
import com.example.portal.shared.utils.PromptUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Сервис для построения диаграммы прецедентов (Use Case diagram).
 * <p>
 * Промпт загружается из справочника (PromptService) по коду:
 * - "usecase_modeller" — шаблон с правилами построения диаграммы прецедентов (содержит %s)
 */
@Slf4j
@Service
public class UseCaseModellerService {

    private final ChatClient chat;
    private final PromptService promptService;

    @Autowired
    public UseCaseModellerService(ChatClient.Builder builder, PromptService promptService) {
        this.chat = builder.build();
        this.promptService = promptService;
    }

    /**
     * Генерирует PlantUML-диаграмму прецедентов на основе нарратива и доменной модели.
     */
    public String generateUseCasePlantUml(String narrative, String domainModelPlantUml, String ragContext) {
        // Загружаем промпт из БД (или кэша)
        String promptTemplate = promptService.getByCode("usecase_modeller");

        // Экранируем все параметры перед вставкой
        String safeNarrative = PromptUtils.fullEscape(narrative);
        String safeDomainModel = PromptUtils.fullEscape(domainModelPlantUml);
        String safeRagContext = PromptUtils.fullEscape(ragContext.isBlank() ? "нет" : ragContext);

        String userPrompt = String.format(promptTemplate, safeNarrative, safeDomainModel, safeRagContext);

        return chat.prompt()
                .user(PromptUtils.stEscape(userPrompt))
                .options(OpenAiChatOptions.builder()
                        .temperature(1.0)
                        .build())
                .call()
                .content();
    }
}
