package com.example.workflow;

import com.example.portal.agents.iconix.service.agentservices.NarrativeWriterService;
import com.example.portal.agents.iconix.worker.Worker;
import com.example.portal.shared.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

// @Component - отключено, используется версия из com.example.portal.agents.iconix.worker
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
        String description = args != null && args.containsKey("description")
                ? String.valueOf(args.get("description"))
                : ctx.goal;

        var rag = ragService.retrieveContext(description, 4);
        ctx.log(String.format("rag.narrative: fragments=%d, vs=%s",
                rag.fragmentsCount(),
                rag.vectorStoreAvailable()));

        String generated = narrativeWriter.composeNarrative(
                description,
                ctx.goal,
                rag.text()
        );

        ctx.overrideNarrative(generated);
        ctx.log("narrative.generated: chars=" + generated.length());
    }
}

