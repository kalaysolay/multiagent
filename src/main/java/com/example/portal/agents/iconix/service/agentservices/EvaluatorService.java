package com.example.portal.agents.iconix.service.agentservices;

import com.example.portal.agents.iconix.model.Issue;
import com.example.portal.prompt.service.PromptService;
import com.example.portal.shared.utils.PromptUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Сервис-оценщик: проводит ревью PlantUML-модели или нарратива.
 * <p>
 * Промпты загружаются из справочника (PromptService) по кодам:
 * - "evaluator_plantuml"  — промпт для оценки PlantUML (содержит %s)
 * - "evaluator_narrative" — промпт для оценки нарратива (содержит %s)
 */
@Slf4j
@Service
public class EvaluatorService {

    private final ChatClient chat;
    private final PromptService promptService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public EvaluatorService(ChatClient.Builder builder, PromptService promptService) {
        this.chat = builder.build();
        this.promptService = promptService;
    }

    /**
     * Оценивает PlantUML-модель на соответствие нарративу.
     * Возвращает список замечаний (Issue).
     */
    public List<Issue> evaluatePlantUml(String narrative, String ragContext, String plantUml) {
        // Экранируем все параметры перед вставкой в String.format
        String safeNarrative = PromptUtils.fullEscape(narrative);
        String safeRagContext = PromptUtils.fullEscape(normalizeContext(ragContext));
        String safePlantUml = PromptUtils.fullEscape(truncateIfNeeded(plantUml, 50000));

        // Загружаем промпт из БД (или кэша)
        String promptTemplate = promptService.getByCode("evaluator_plantuml");

        String prompt = String.format(promptTemplate, safeNarrative, safeRagContext, safePlantUml);

        return parseIssuesFromResponse(prompt);
    }

    /**
     * Оценивает качество нарратива.
     * Возвращает список замечаний (Issue).
     */
    public List<Issue> evaluateNarrative(String narrative, String ragContext) {
        String safeNarrative = PromptUtils.fullEscape(narrative);
        String safeRagContext = PromptUtils.fullEscape(normalizeContext(ragContext));

        // Загружаем промпт из БД (или кэша)
        String promptTemplate = promptService.getByCode("evaluator_narrative");

        String prompt = String.format(promptTemplate, safeNarrative, safeRagContext);

        return parseIssuesFromResponse(prompt);
    }
    
    /**
     * Парсит ответ от LLM в список Issue с обработкой ошибок и попыткой исправления JSON.
     */
    private List<Issue> parseIssuesFromResponse(String prompt) {
        try {
            String response = chat.prompt()
                    .user(PromptUtils.stEscape(prompt))
                    .options(OpenAiChatOptions.builder()
                            .temperature(1.0)
                            .build())
                    .call()
                    .content();
            
            log.debug("Raw LLM response: {}", response);
            
            // Очищаем ответ от markdown форматирования, если есть
            String cleanedJson = cleanJsonResponse(response);
            log.debug("Cleaned JSON: {}", cleanedJson);
            
            // Пытаемся исправить распространенные ошибки JSON
            String fixedJson = fixCommonJsonErrors(cleanedJson);
            if (!cleanedJson.equals(fixedJson)) {
                log.debug("Fixed JSON (was: {}): {}", cleanedJson, fixedJson);
            }
            
            // Парсим JSON
            Issue[] issues = objectMapper.readValue(fixedJson, Issue[].class);
            log.info("Successfully parsed {} issues from LLM response", issues != null ? issues.length : 0);
            return issues == null ? List.of() : Arrays.asList(issues);
            
        } catch (Exception e) {
            log.error("Failed to parse issues from LLM response. Error: {}", e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * Очищает ответ от markdown форматирования и лишнего текста.
     */
    private String cleanJsonResponse(String response) {
        if (response == null || response.isBlank()) {
            return "[]";
        }
        
        String cleaned = response.replaceAll("```json\\s*", "")
                                 .replaceAll("```\\s*", "")
                                 .trim();
        
        int startIdx = cleaned.indexOf('[');
        int endIdx = cleaned.lastIndexOf(']');
        
        if (startIdx >= 0 && endIdx > startIdx) {
            cleaned = cleaned.substring(startIdx, endIdx + 1);
        }
        
        return cleaned;
    }
    
    /**
     * Исправляет распространенные ошибки в JSON, такие как отсутствующие запятые.
     */
    private String fixCommonJsonErrors(String json) {
        if (json == null || json.isBlank()) {
            return "[]";
        }
        
        json = Pattern.compile("}\\s*\\{")
                .matcher(json)
                .replaceAll("}, {");
        
        json = Pattern.compile("(\"[^\"]+\")\\s*(\"[a-z]+)\":", Pattern.CASE_INSENSITIVE)
                .matcher(json)
                .replaceAll("$1, $2:");
        
        json = Pattern.compile("(\\d+)\\s*(\"[a-z]+)\":", Pattern.CASE_INSENSITIVE)
                .matcher(json)
                .replaceAll("$1, $2:");
        
        json = Pattern.compile("(true|false|null)\\s*(\"[a-z]+)\":", Pattern.CASE_INSENSITIVE)
                .matcher(json)
                .replaceAll("$1, $2:");
        
        return json;
    }

    private static String normalizeContext(String ragContext) {
        return (ragContext == null || ragContext.isBlank()) ? "нет" : ragContext;
    }

    private static String truncateIfNeeded(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n\n[... текст обрезан для экономии токенов ...]";
    }
}
