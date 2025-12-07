package com.example.portal.agents.iconix.worker;

import com.example.portal.agents.iconix.model.Issue;
import com.example.portal.agents.iconix.service.agentservices.DomainModellerService;
import com.example.portal.shared.service.OpenAiRagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ModelWorker implements Worker {

    private final DomainModellerService modeller;
    private final OpenAiRagService ragService;

    @Override public String name() { return "model"; }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Context ctx, Map<String, Object> args) {
        String mode = String.valueOf(args.getOrDefault("mode", "generate")); // generate|refine
        String plant = (String) ctx.state.get("plantuml");
        String narrative = ctx.narrativeEffective();

        var rag = ragService.retrieveContext(narrative, 4);
        ctx.log(String.format("rag.model: fragments=%d, vs=%s",
                rag.fragmentsCount(),
                rag.vectorStoreAvailable()));
        String ragContext = rag.text();

        if ("generate".equalsIgnoreCase(mode) || plant == null || plant.isBlank()) {
            plant = modeller.generateIconixPlantUml(narrative, ragContext);
            ctx.log("model.generate: " + plant.length() + " chars");
        } else {
            // 1) пробуем взять уже готовый List<Issue> из контекста
            List<Issue> issues = (List<Issue>) ctx.state.get("issues");

            // 2) иначе маппим из «сырого» вида (List<Map>)
            if (issues == null) {
                List<Map<String, Object>> raw = (List<Map<String, Object>>) ctx.state.get("issuesRaw");
                issues = mapIssues(raw);
            }

            plant = modeller.refineModelWithIssues(narrative, plant, issues, ragContext);
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

