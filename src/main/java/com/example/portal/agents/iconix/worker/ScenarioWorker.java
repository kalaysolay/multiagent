package com.example.portal.agents.iconix.worker;

import com.example.portal.agents.iconix.service.UseCaseScenarioService;
import com.example.portal.agents.iconix.service.agentservices.ScenarioWriterService;
import com.example.portal.shared.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        
        // Извлекаем первый Use Case из диаграммы для подписи сценария (избегаем "Без названия")
        var firstUseCase = extractFirstUseCase(useCaseModel);
        scenarioService.saveScenario(ctx.requestId, firstUseCase.alias(), firstUseCase.name(), scenario);
        
        ctx.state.put("scenario", scenario);
        ctx.log("scenario.generate: " + scenario.length() + " chars");
    }

    /** Извлекает первый Use Case из PlantUML (usecase "Name" as alias). */
    private static FirstUseCase extractFirstUseCase(String useCaseModel) {
        if (useCaseModel == null || useCaseModel.isBlank()) {
            return new FirstUseCase("general", "Сводный сценарий");
        }
        // usecase "Название" as alias или usecase "Название" as alias << base >>
        Pattern p = Pattern.compile("usecase\\s+\"([^\"]+)\"\\s+as\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(useCaseModel);
        if (m.find()) {
            return new FirstUseCase(m.group(2), m.group(1).trim());
        }
        // Без "as alias"
        Pattern p2 = Pattern.compile("usecase\\s+\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(useCaseModel);
        if (m2.find()) {
            String name = m2.group(1).trim();
            String alias = name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            if (alias.isEmpty()) alias = "general";
            return new FirstUseCase(alias, name);
        }
        return new FirstUseCase("general", "Сводный сценарий");
    }

    private static record FirstUseCase(String alias, String name) {}
}

