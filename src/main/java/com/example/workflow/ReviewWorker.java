package com.example.workflow;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReviewWorker implements Worker {

    private final EvaluatorService evaluator;

    @Override public String name() { return "review"; }

    @Override
    public void execute(Context ctx, Map<String, Object> args) {
        String target = String.valueOf(args.getOrDefault("target", "model"));

        if ("narrative".equalsIgnoreCase(target)) {
            List<Issue> issues = evaluator.evaluateNarrative(ctx.narrative);
            ctx.state.put("narrativeIssues", issues);
            ctx.log("review.narrative: issues=" + issues.size());
            return;
        }

        String plant = (String) ctx.state.get("plantuml");
        if (plant == null || plant.isBlank())
            throw new IllegalStateException("No PlantUML in context; run model first.");

        List<Issue> issues = evaluator.evaluatePlantUml(ctx.narrative, plant);
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
