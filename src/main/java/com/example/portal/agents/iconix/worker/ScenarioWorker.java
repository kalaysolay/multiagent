package com.example.portal.agents.iconix.worker;

import com.example.portal.agents.iconix.service.UseCaseScenarioService;
import com.example.portal.agents.iconix.service.agentservices.ScenarioWriterService;
import com.example.portal.shared.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScenarioWorker implements Worker {

    private final ScenarioWriterService scenarioWriter;
    private final RagService ragService;
    private final UseCaseScenarioService scenarioService;

    @Override
    public String name() {
        return "scenario";
    }

    @Override
    public void execute(Context ctx, Map<String, Object> args) {
        String narrative = ctx.narrativeEffective();
        String domainModel = (String) ctx.state.get("plantuml");
        String useCaseModel = (String) ctx.state.get("useCaseModel");
        String mvcModel = (String) ctx.state.get("mvcDiagram");
        
        if (domainModel == null || domainModel.isBlank()) {
            throw new IllegalStateException("No domain model (plantuml) in context; run model first.");
        }
        
        if (useCaseModel == null || useCaseModel.isBlank()) {
            throw new IllegalStateException("No use case model (useCaseModel) in context; run usecase first.");
        }
        
        if (mvcModel == null || mvcModel.isBlank()) {
            throw new IllegalStateException("No MVC model (mvcDiagram) in context; run mvc first.");
        }

        var rag = ragService.retrieveContext(narrative, 4);
        ctx.log(String.format("rag.scenario: fragments=%d, vs=%s",
                rag.fragmentsCount(),
                rag.vectorStoreAvailable()));
        String ragContext = rag.text();

        String scenario = scenarioWriter.generateScenario(narrative, domainModel, useCaseModel, mvcModel, ragContext);
        
        // Сохраняем сценарий в БД
        scenarioService.saveScenario(ctx.requestId, scenario);
        
        ctx.state.put("scenario", scenario);
        ctx.log("scenario.generate: " + scenario.length() + " chars");
    }
}

