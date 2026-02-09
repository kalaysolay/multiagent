package com.example.portal.agents.iconix.service.agentservices;

import com.example.portal.agents.iconix.model.Issue;
import com.example.portal.prompt.service.PromptService;
import com.example.portal.shared.utils.PromptUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис для построения доменной модели ICONIX (PlantUML).
 * <p>
 * Промпты загружаются из справочника (PromptService) по кодам:
 * - "domain_modeller_system"   — системный промпт (роль ИИ + правила)
 * - "domain_modeller_generate" — пользовательский промпт для генерации (содержит %s)
 * - "domain_modeller_refine"   — пользовательский промпт для уточнения (содержит %s)
 */
@Slf4j
@Service
public class DomainModellerService {

    private final ChatClient chat;
    private final PromptService promptService;

    @Autowired
    public DomainModellerService(ChatClient.Builder builder, PromptService promptService) {
        this.chat = builder.build();
        this.promptService = promptService;
    }

    /**
     * Генерирует PlantUML доменной модели на основе нарратива и RAG-контекста.
     */
    public String generateIconixPlantUml(String narrative, String ragContext) {
        // Загружаем промпты из БД (или кэша)
        String systemPrompt = promptService.getByCode("domain_modeller_system");
        String userPromptTemplate = promptService.getByCode("domain_modeller_generate");

        String userPromptRaw = String.format(userPromptTemplate, narrative, normalizeContext(ragContext));

        return chat.prompt()
                .system(PromptUtils.stEscape(systemPrompt))
                .user(PromptUtils.stEscape(userPromptRaw))
                .options(OpenAiChatOptions.builder()
                        .temperature(1.0)
                        .build())
                .call()
                .content();
    }

    /**
     * Уточняет существующую доменную модель с учётом замечаний от EvaluatorService.
     */
    public String refineModelWithIssues(String narrative, String currentPlantUml, List<Issue> issues, String ragContext) {
        StringBuilder sb = new StringBuilder();
        if (issues == null || issues.isEmpty()) {
            sb.append("нет");
        } else {
            for (Issue i : issues) {
                sb.append("- ").append(i.title()).append(" ⇒ ").append(i.suggestion())
                        .append(" (severity=").append(i.severity()).append(")").append("\n");
            }
        }

        // Загружаем промпты из БД (или кэша)
        String systemPrompt = promptService.getByCode("domain_modeller_system");
        String userPromptTemplate = promptService.getByCode("domain_modeller_refine");

        String userPromptRaw = String.format(userPromptTemplate,
                narrative, currentPlantUml, sb.toString(), normalizeContext(ragContext));

        return chat.prompt()
                .system(PromptUtils.stEscape(systemPrompt))
                .user(PromptUtils.stEscape(userPromptRaw))
                .options(OpenAiChatOptions.builder()
                        .temperature(1.0)
                        .build())
                .call()
                .content();
    }

    private static String normalizeContext(String ragContext) {
        return (ragContext == null || ragContext.isBlank()) ? "нет" : ragContext;
    }
}
