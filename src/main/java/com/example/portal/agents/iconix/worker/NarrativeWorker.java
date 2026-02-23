package com.example.portal.agents.iconix.worker;

import com.example.portal.agents.iconix.service.agentservices.NarrativeWriterService;
import com.example.portal.shared.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class NarrativeWorker implements Worker {

    private final NarrativeWriterService narrativeWriter;
    private final RagService ragService;

    @Override
    public String name() {
        return "narrative";
    }

    @Override
    public void execute(Context ctx, Map<String, Object> args) {
        // Описание для нарратива: goal и опционально description из MCP
        String description = firstNonBlank(
                args != null ? (String) args.get("description") : null,
                ctx.goal
        );
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

