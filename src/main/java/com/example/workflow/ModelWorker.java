package com.example.workflow;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ModelWorker implements Worker {

    private final DomainModellerService modeller;

    @Override public String name() { return "model"; }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Context ctx, Map<String, Object> args) {
        String mode = String.valueOf(args.getOrDefault("mode", "generate")); // generate|refine
        String plant = (String) ctx.state.get("plantuml");

        if ("generate".equalsIgnoreCase(mode) || plant == null || plant.isBlank()) {
            plant = modeller.generateIconixPlantUml(ctx.narrative);
            ctx.log("model.generate: " + plant.length() + " chars");
        } else {
            // 1) пробуем взять уже готовый List<Issue> из контекста
            List<Issue> issues = (List<Issue>) ctx.state.get("issues");

            // 2) иначе маппим из «сырого» вида (List<Map>)
            if (issues == null) {
                List<Map<String, Object>> raw = (List<Map<String, Object>>) ctx.state.get("issuesRaw");
                issues = mapIssues(raw);
            }

            plant = modeller.refineModelWithIssues(ctx.narrative, plant, issues);
            ctx.log("model.refine: " + plant.length() + " chars");
        }
        ctx.state.put("plantuml", plant);
    }

    private List<Issue> mapIssues(List<Map<String, Object>> raw) {
        if (raw == null) return List.of();
        return raw.stream()
                .map(m -> new Issue(
                        (String) m.getOrDefault("id", UUID.randomUUID().toString()),
                        (String) m.getOrDefault("title", ""),
                        (String) m.getOrDefault("severity", "minor"),
                        (String) m.getOrDefault("suggestion", "") // ВАЖНО: поле называется suggestion
                ))
                .collect(Collectors.toList()); // чтобы тип был List<Issue>, а не List<Object>
    }
}

