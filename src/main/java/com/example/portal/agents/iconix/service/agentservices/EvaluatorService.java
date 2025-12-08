package com.example.portal.agents.iconix.service.agentservices;

import com.example.portal.agents.iconix.model.Issue;
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

@Slf4j
@Service
public class EvaluatorService {

    private final ChatClient chat;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public EvaluatorService(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    public List<Issue> evaluatePlantUml(String narrative, String ragContext, String plantUml) {
        // Экранируем все параметры перед вставкой в String.format
        String safeNarrative = PromptUtils.fullEscape(narrative);
        String safeRagContext = PromptUtils.fullEscape(normalizeContext(ragContext));
        // PlantUML может быть очень большим, обрезаем до разумного размера и экранируем
        String safePlantUml = PromptUtils.fullEscape(truncateIfNeeded(plantUml, 50000));

        String prompt = String.format("""
Ты — аналитик требований. Проведи ревью пользовательского нарратива и оцени его качество.
Верни ТОЛЬКО валидный JSON-массив объектов без дополнительного текста, комментариев или форматирования.

Формат ответа (строго соблюдай синтаксис JSON):
[
  {
    "id": "1",
    "title": "Название проблемы",
    "severity": "LOW",
    "suggestion": "Конкретное предложение по улучшению"
  },
  {
    "id": "2",
    "title": "Другая проблема",
    "severity": "MEDIUM",
    "suggestion": "Другое предложение"
  }
]

ВАЖНО:
- Каждое поле должно быть в кавычках
- После каждого поля (кроме последнего в объекте) должна быть запятая
- severity может быть только: LOW, MEDIUM, HIGH
- Верни ТОЛЬКО JSON, без пояснений, без markdown форматирования, без кодовых блоков

Правила оценки:
- Указывай проблемы неполноты, неоднозначности, противоречий, отсутствующих бизнес-правил.
- severity ∈ {LOW, MEDIUM, HIGH}.
- suggestion — конкретное действие, которое улучшит нарратив.

Нарратив:
%s

Контекст системы (RAG):
%s

PlantUML для оценки:
%s
""", safeNarrative, safeRagContext, safePlantUml);

        return parseIssuesFromResponse(prompt);
    }

    public List<Issue> evaluateNarrative(String narrative, String ragContext) {
        String safeNarrative = PromptUtils.fullEscape(narrative);
        String safeRagContext = PromptUtils.fullEscape(normalizeContext(ragContext));

        String prompt = String.format("""
Ты — аналитик требований. Проведи ревью пользовательского нарратива и оцени его качество.
Верни ТОЛЬКО валидный JSON-массив объектов без дополнительного текста, комментариев или форматирования.

Формат ответа (строго соблюдай синтаксис JSON):
[
  {
    "id": "1",
    "title": "Название проблемы",
    "severity": "LOW",
    "suggestion": "Конкретное предложение по улучшению"
  },
  {
    "id": "2",
    "title": "Другая проблема",
    "severity": "MEDIUM",
    "suggestion": "Другое предложение"
  }
]

ВАЖНО:
- Каждое поле должно быть в кавычках
- После каждого поля (кроме последнего в объекте) должна быть запятая
- severity может быть только: LOW, MEDIUM, HIGH
- Верни ТОЛЬКО JSON, без пояснений, без markdown форматирования, без кодовых блоков

Правила оценки:
- Указывай проблемы неполноты, неоднозначности, противоречий, отсутствующих бизнес-правил.
- severity ∈ {LOW, MEDIUM, HIGH}.
- suggestion — конкретное действие, которое улучшит нарратив.

Нарратив:
%s

Контекст системы (RAG):
%s
""", safeNarrative, safeRagContext);

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
            // Возвращаем пустой список вместо падения всего workflow
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
        
        // Убираем markdown code blocks
        String cleaned = response.replaceAll("```json\\s*", "")
                                 .replaceAll("```\\s*", "")
                                 .trim();
        
        // Ищем JSON массив (начинается с [)
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
        
        // Исправляем отсутствующие запятые между объектами в массиве
        // Паттерн: } без запятой перед следующей {
        json = Pattern.compile("}\\s*\\{")
                .matcher(json)
                .replaceAll("}, {");
        
        // Исправляем отсутствующие запятые после строковых значений перед следующим полем
        // Паттерн: "значение" без запятой перед "ключ"
        // Это исправляет случаи типа: "suggestion": "текст" "title": "текст"
        json = Pattern.compile("(\"[^\"]+\")\\s*(\"[a-z]+)\":", Pattern.CASE_INSENSITIVE)
                .matcher(json)
                .replaceAll("$1, $2:");
        
        // Исправляем отсутствующие запятые после числовых значений
        // Паттерн: число без запятой перед "ключ"
        json = Pattern.compile("(\\d+)\\s*(\"[a-z]+)\":", Pattern.CASE_INSENSITIVE)
                .matcher(json)
                .replaceAll("$1, $2:");
        
        // Исправляем отсутствующие запятые после boolean/null значений
        // Паттерн: true/false/null без запятой перед "ключ"
        json = Pattern.compile("(true|false|null)\\s*(\"[a-z]+)\":", Pattern.CASE_INSENSITIVE)
                .matcher(json)
                .replaceAll("$1, $2:");
        
        return json;
    }

    private static String normalizeContext(String ragContext) {
        return (ragContext == null || ragContext.isBlank()) ? "нет" : ragContext;
    }

    /**
     * Обрезает строку до максимальной длины, если она превышает лимит.
     * Добавляет суффикс, указывающий на обрезку.
     */
    private static String truncateIfNeeded(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n\n[... текст обрезан для экономии токенов ...]";
    }
}

