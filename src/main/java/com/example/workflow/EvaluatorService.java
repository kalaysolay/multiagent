package com.example.workflow;

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

        String prompt = String.format("""
Ты — аналитик требований. Проведи ревью пользовательского нарратива и оцени его качество.
Верни JSON-массив объектов {{id, title, severity, suggestion}}.
Правила:
- Указывай проблемы неполноты, неоднозначности, противоречий, отсутствующих бизнес-правил.
- severity ∈ {{LOW, MEDIUM, HIGH}}.
- suggestion — конкретное действие, которое улучшит нарратив.

Нарратив:
%s

Контекст системы (RAG):
%s

PlantUML для оценки:
%s
""", narrative, normalizeContext(ragContext), plantUml);

        Issue[] issues = chat.prompt()
                .user(PromptUtils.stEscape(prompt))
                .options(OpenAiChatOptions.builder()
                        .temperature(1.0)
                        .build())
                .call()
                .entity(Issue[].class);
        return issues == null ? List.of() : Arrays.asList(issues);
    }

    public List<Issue> evaluateNarrative(String narrative, String ragContext) {

        String prompt = String.format("""
Ты — аналитик требований. Проведи ревью пользовательского нарратива и оцени его качество.
Верни JSON-массив объектов {{id, title, severity, suggestion}}.
Правила:
- Указывай проблемы неполноты, неоднозначности, противоречий, отсутствующих бизнес-правил.
- severity ∈ {{LOW, MEDIUM, HIGH}}.
- suggestion — конкретное действие, которое улучшит нарратив.

Нарратив:
%s

Контекст системы (RAG):
%s
""", narrative, normalizeContext(ragContext));

        Issue[] issues = chat.prompt()
                .user(PromptUtils.stEscape(prompt))
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
}