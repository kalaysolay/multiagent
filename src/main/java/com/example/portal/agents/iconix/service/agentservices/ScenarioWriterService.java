package com.example.portal.agents.iconix.service.agentservices;

import com.example.portal.prompt.service.PromptService;
import com.example.portal.shared.utils.PromptUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Сервис для генерации сценариев Use Case по Алистару Коберну.
 * <p>
 * Промпт загружается из справочника (PromptService) по коду:
 * - "scenario_writer" — шаблон с правилами формирования сценария (содержит %s)
 */
@Slf4j
@Service
public class ScenarioWriterService {

    private final ChatClient chat;
    private final PromptService promptService;

    @Autowired
    public ScenarioWriterService(ChatClient.Builder builder, PromptService promptService) {
        this.chat = builder.build();
        this.promptService = promptService;
    }

    /**
     * Генерирует сценарий Use Case на основе всех артефактов ICONIX-процесса.
     */
    public String generateScenario(String narrative, String domainModelPlantUml,
                                   String useCaseModelPlantUml, String mvcModelPlantUml,
                                   String ragContext) {
        // Загружаем промпт из БД (или кэша)
        String promptTemplate = promptService.getByCode("scenario_writer");

        // Экранируем все % в шаблоне, кроме %s (спецификаторов формата)
        String escapedTemplate = promptTemplate
                .replace("%s", "___PLACEHOLDER_S___")
                .replace("%", "%%")
                .replace("___PLACEHOLDER_S___", "%s");

        // Экранируем все параметры перед вставкой
        String safeNarrative = PromptUtils.fullEscape(narrative);
        String safeDomainModel = PromptUtils.fullEscape(domainModelPlantUml);
        String safeUseCaseModel = PromptUtils.fullEscape(useCaseModelPlantUml);
        String safeMvcModel = PromptUtils.fullEscape(mvcModelPlantUml);
        String safeRagContext = PromptUtils.fullEscape(ragContext.isBlank() ? "нет" : ragContext);

        String userPrompt = String.format(escapedTemplate,
                safeNarrative, safeDomainModel, safeUseCaseModel, safeMvcModel, safeRagContext);

        return chat.prompt()
                .user(PromptUtils.stEscape(userPrompt))
                .options(OpenAiChatOptions.builder()
                        .temperature(1.0)
                        .build())
                .call()
                .content();
    }
}
