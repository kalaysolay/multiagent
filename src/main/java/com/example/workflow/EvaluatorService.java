package com.example.workflow;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EvaluatorService {

    private final ChatClient chat;

    @Autowired
    public EvaluatorService(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    public List<Issue> evaluatePlantUml(String narrative, String plantUml) {
        String prompt = String.format("""
Ты — ревьюер ICONIX. Проверь соответствие доменной модели нарративу.
Верни JSON-массив объектов {{id, title, severity, suggestion}}.
Правила:
- Критика по полноте субъектов/объектов, атрибутам, связям и кратностям.
- severity ∈ {{LOW, MEDIUM, HIGH}}.
- suggestion — конкретное действие (например: "добавить связь Order - Customer 1..*").

Нарратив:
%s

PlantUML:
%s
""", narrative, plantUml);

        Issue[] issues = chat.prompt()
                .user(prompt)
                .options(OpenAiChatOptions.builder()
                        .temperature(1.0)
                        .build())
                .call()
                .entity(Issue[].class);return issues == null ? List.of() : Arrays.asList(issues);
    }
}
