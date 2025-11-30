package com.example.workflow.agentsServices;

import com.example.workflow.Issue;
import com.example.workflow.PromptUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class EvaluatorService {

    private final ChatClient chat;

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
Верни JSON-массив объектов {id, title, severity, suggestion}.
Правила:
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

        Issue[] issues = chat.prompt()
                .user(PromptUtils.stEscape(prompt)) // Дополнительное экранирование для ST4
                .options(OpenAiChatOptions.builder()
                        .temperature(1.0)
                        .build())
                .call()
                .entity(Issue[].class);
        return issues == null ? List.of() : Arrays.asList(issues);
    }

    public List<Issue> evaluateNarrative(String narrative, String ragContext) {
        String safeNarrative = PromptUtils.fullEscape(narrative);
        String safeRagContext = PromptUtils.fullEscape(normalizeContext(ragContext));

        String prompt = String.format("""
Ты — аналитик требований. Проведи ревью пользовательского нарратива и оцени его качество.
Верни JSON-массив объектов {id, title, severity, suggestion}.
Правила:
- Указывай проблемы неполноты, неоднозначности, противоречий, отсутствующих бизнес-правил.
- severity ∈ {LOW, MEDIUM, HIGH}.
- suggestion — конкретное действие, которое улучшит нарратив.

Нарратив:
%s

Контекст системы (RAG):
%s
""", safeNarrative, safeRagContext);

        Issue[] issues = chat.prompt()
                .user(PromptUtils.stEscape(prompt)) // Экранируем фигурные скобки для ST4
                .options(OpenAiChatOptions.builder()
                        .temperature(1.0)
                        .build())
                .call()
                .entity(Issue[].class);
        return issues == null ? List.of() : Arrays.asList(issues);
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