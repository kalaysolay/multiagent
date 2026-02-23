package com.example.workflow;

import com.example.portal.agents.iconix.model.Issue;
import com.example.portal.agents.iconix.service.agentservices.EvaluatorService;
import com.example.portal.agents.iconix.worker.Worker;
import com.example.portal.shared.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// @Component - отключено, используется версия из com.example.portal.agents.iconix.worker
@RequiredArgsConstructor
public class ReviewWorker implements Worker {

    private final EvaluatorService evaluator;
    private final RagService ragService;

    @Override public String name() { return "review"; }

    @Override
    public void execute(Context ctx, Map<String, Object> args) {
        String target = String.valueOf(args.getOrDefault("target", "model"));
        String narrative = ctx.narrativeEffective();

        var rag = ragService.retrieveContext(narrative, 4);
        ctx.log(String.format("rag.review: fragments=%d, vs=%s",
                rag.fragmentsCount(),
                rag.vectorStoreAvailable()));
        String ragContext = rag.text();

        if ("narrative".equalsIgnoreCase(target)) {
            List<Issue> issues = evaluator.evaluateNarrative(narrative, ragContext);
            ctx.state.put("narrativeIssues", issues);
            ctx.log("review.narrative: issues=" + issues.size());
            return;
        }

        String plant = (String) ctx.state.get("plantuml");
        if (plant == null || plant.isBlank())
            throw new IllegalStateException("No PlantUML in context; run model first.");

        List<Issue> issues = evaluator.evaluatePlantUml(narrative, ragContext, plant);
        ctx.state.put("issues", issues);

        // «Сырые» данные — с теми же ключами, что ожидает mapIssues()
        List<Map<String, Object>> raw = issues.stream()
                .map(i -> Map.<String, Object>of(
                        "id",         i.id(),
                        "title",      i.title(),
                        "severity",   i.severity(),
                        "suggestion", i.suggestion()
                ))
                .collect(Collectors.toList());

        ctx.state.put("issuesRaw", raw);
        ctx.log("review.model: issues=" + issues.size());
    }
}
