package com.example.portal.agents.iconix.worker;

import com.example.portal.agents.iconix.service.agentservices.NarrativeWriterService;
import com.example.portal.shared.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NarrativeWorker implements Worker {

    private final NarrativeWriterService narrativeWriter;
    private final RagService ragService;

    private static final String NO_GOAL_MESSAGE =
            "Цель или описание задачи не указаны. Введите текст в поле «Цель / Запрос» и запустите workflow снова.";

    @Override
    public String name() {
        return "narrative";
    }

    @Override
    public void execute(Context ctx, Map<String, Object> args) {
        // Единственный ввод от пользователя на этом шаге — ctx.goal
        String description = firstNonBlank(
                args != null ? (String) args.get("description") : null,
                ctx.goal
        );
        log.info("NarrativeWorker: ctx.goal length={}, description length={}, preview: {}",
                ctx.goal != null ? ctx.goal.length() : 0,
                description != null ? description.length() : 0,
                description != null && !description.isBlank() ? description.substring(0, Math.min(80, description.length())) + (description.length() > 80 ? "..." : "") : "(empty)");

        if (description == null || description.isBlank()) {
            log.warn("NarrativeWorker: цель пустая, не вызываем LLM");
            ctx.log("narrative.skipped: goal empty");
            ctx.overrideNarrative(NO_GOAL_MESSAGE);
            return;
        }

        String ragQuery = ctx.goal != null && !ctx.goal.isBlank() ? ctx.goal : description;
        if (ragQuery == null) ragQuery = "";

        var rag = ragService.retrieveContext(ragQuery, 4);
        String ragContext = rag.text();
        ctx.log(String.format("rag.narrative: fragments=%d, vs=%s",
                rag.fragmentsCount(),
                rag.vectorStoreAvailable()));

        String generated = narrativeWriter.composeNarrative(
                description,
                ctx.goal,
                ragContext
        );

        ctx.overrideNarrative(generated);
        ctx.log("narrative.generated: chars=" + generated.length());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }
}

