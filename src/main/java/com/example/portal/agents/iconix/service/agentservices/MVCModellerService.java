package com.example.portal.agents.iconix.service.agentservices;

import com.example.portal.prompt.service.PromptService;
import com.example.portal.shared.utils.PromptUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Сервис для построения MVC-диаграмм (диаграмм пригодности / Robustness diagram).
 * <p>
 * Промпт загружается из справочника (PromptService) по коду:
 * - "mvc_modeller" — шаблон с правилами построения MVC-диаграмм (содержит %s)
 */
@Slf4j
@Service
public class MVCModellerService {

    private final ChatClient chat;
    private final PromptService promptService;

    @Autowired
    public MVCModellerService(ChatClient.Builder builder, PromptService promptService) {
        this.chat = builder.build();
        this.promptService = promptService;
    }

    /**
     * Генерирует MVC PlantUML-диаграммы для всех базовых прецедентов.
     */
    public String generateMVCPlantUml(String narrative, String domainModelPlantUml,
                                      String useCaseModelPlantUml, String ragContext) {
        // Загружаем промпт из БД (или кэша)
        String promptTemplate = promptService.getByCode("mvc_modeller");

        // Экранируем все параметры перед вставкой
        String safeNarrative = PromptUtils.fullEscape(narrative);
        String safeDomainModel = PromptUtils.fullEscape(domainModelPlantUml);
        String safeUseCaseModel = PromptUtils.fullEscape(useCaseModelPlantUml);
        String safeRagContext = PromptUtils.fullEscape(ragContext.isBlank() ? "нет" : ragContext);

        String userPrompt = String.format(promptTemplate,
                safeNarrative, safeDomainModel, safeUseCaseModel, safeRagContext);

        return chat.prompt()
                .user(PromptUtils.stEscape(userPrompt))
                .options(OpenAiChatOptions.builder()
                        .temperature(1.0)
                        .build())
                .call()
                .content();
    }
}
