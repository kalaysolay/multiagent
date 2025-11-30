package com.example.workflow.agentsServices;

import com.example.workflow.PromptUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NarrativeWriterService {

    private final ChatClient chat;

    @Autowired
    public NarrativeWriterService(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    public String composeNarrative(String taskDescription, String goal, String ragContext) {
        String prompt = String.format("""
Ты — методолог процессов комплаенс и аналитик требований.
На основе описания задачи и цели сформируй подробный пользовательский нарратив для ICONIX-моделирования.
Обязательно используй термины и функциональность из предоставленного контекста системы (если он есть).
Структура ответа:
- 3–5 абзацев, каждый описывает последовательность действий пользователя и системные реакции.
- Упоминай ключевые роли, UI-экраны, интеграции и сущности из контекста.
- Не углубляйся слишком в технику. Глоссарий - для бизнес-заказчика, а не для разработчиков. Но имей ввиду, что на основе этого нарратива будет составляться доменная модель.

Описание задачи:
%s

Бизнес-цель:
%s

Контекст системы (RAG):
%s

Выведи только текст нарратива без списков, заголовков и пояснений.
""", safe(taskDescription), safe(goal), normalizeContext(ragContext));

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

