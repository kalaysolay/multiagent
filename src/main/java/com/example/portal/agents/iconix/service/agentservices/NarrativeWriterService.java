package com.example.portal.agents.iconix.service.agentservices;

import com.example.portal.prompt.service.PromptService;
import com.example.portal.shared.utils.PromptUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Сервис для генерации пользовательского нарратива для ICONIX-моделирования.
 * <p>
 * Промпт загружается из справочника (PromptService) по коду "narrative_writer".
 * Если в шаблоне из БД нет ровно трёх плейсхолдеров %s (описание, цель, RAG), используется встроенный шаблон,
 * чтобы описание задачи и цель пользователя всегда попадали в запрос к LLM.
 */
@Slf4j
@Service
public class NarrativeWriterService {

    private static final int REQUIRED_PLACEHOLDERS = 3;

    /** Встроенный шаблон с тремя %s: описание задачи, бизнес-цель, контекст RAG. Используется, если в БД старый шаблон без плейсхолдеров. */
    private static final String DEFAULT_NARRATIVE_TEMPLATE = """
Ты — методолог процессов комплаенс и аналитик требований.
На основе описания задачи и цели сформируй подробный пользовательский нарратив для ICONIX-моделирования.

Описание задачи и бизнес-цель от пользователя уже указаны ниже. Не проси прислать описание, пользовательскую историю или цель — используй только то, что дано в блоках «Описание задачи» и «Бизнес-цель».

Важно про контекст системы (RAG):
- Если контекст системы указан и не равен «нет» — используй только термины, сущности, роли и функциональность из этого контекста.
- Если контекст системы отсутствует (указано «нет» или он пуст) — опирайся только на описание задачи и цель; не придумывай сущности, которых нет в задании.

Структура ответа:
- 3–5 абзацев, каждый описывает последовательность действий пользователя и системные реакции.
- Упоминай только те роли, UI-экраны и сущности, которые есть в контексте системы или явно в описании задачи/цели.

Описание задачи:
%s

Бизнес-цель:
%s

Контекст системы (RAG):
%s

Выведи только текст нарратива без списков, заголовков и пояснений.
""";

    private final ChatClient chat;
    private final PromptService promptService;

    @Autowired
    public NarrativeWriterService(ChatClient.Builder builder, PromptService promptService) {
        this.chat = builder.build();
        this.promptService = promptService;
    }

    /**
     * Генерирует подробный пользовательский нарратив на основе описания задачи,
     * бизнес-цели и RAG-контекста.
     */
    public String composeNarrative(String taskDescription, String goal, String ragContext) {
        String promptTemplate = promptService.getByCode("narrative_writer");
        if (countPlaceholders(promptTemplate) < REQUIRED_PLACEHOLDERS) {
            log.warn("Промпт 'narrative_writer' из БД не содержит трёх плейсхолдеров %%s — используем встроенный шаблон, чтобы цель пользователя попала в запрос к LLM");
            promptTemplate = DEFAULT_NARRATIVE_TEMPLATE;
        }
        String prompt = String.format(promptTemplate,
                safe(taskDescription), safe(goal), normalizeContext(ragContext));
        log.debug("Narrative prompt length: {} chars (goal/description included)", prompt.length());
        return chat.prompt()
                .user(PromptUtils.stEscape(prompt))
                .options(OpenAiChatOptions.builder()
                        .temperature(1.0)
                        .build())
                .call()
                .content();
    }

    /** Считает вхождения "%s" в шаблоне (для проверки, что подставляются описание, цель, RAG). */
    private static int countPlaceholders(String template) {
        if (template == null) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = template.indexOf("%s", idx)) != -1) {
            count++;
            idx += 2;
        }
        return count;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeContext(String ragContext) {
        return (ragContext == null || ragContext.isBlank()) ? "нет" : ragContext;
    }
}
