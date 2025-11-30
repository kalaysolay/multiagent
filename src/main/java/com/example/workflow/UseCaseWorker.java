package com.example.workflow;

import com.example.workflow.agentsServices.UseCaseModellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class UseCaseWorker implements Worker {

    private final UseCaseModellerService useCaseModeller;
    private final OpenAiRagService ragService;

    @Override
    public String name() {
        return "usecase";
    }

    @Override
    public void execute(Context ctx, Map<String, Object> args) {
        String narrative = ctx.narrativeEffective();
        String domainModel = (String) ctx.state.get("plantuml");
        
        if (domainModel == null || domainModel.isBlank()) {
            throw new IllegalStateException("No domain model (plantuml) in context; run model first.");
        }

        var rag = ragService.retrieveContext(narrative, 4);
        ctx.log(String.format("rag.usecase: fragments=%d, vs=%s",
                rag.fragmentsCount(),
                rag.vectorStoreAvailable()));
        String ragContext = rag.text();

        String useCasePlantUml = useCaseModeller.generateUseCasePlantUml(narrative, domainModel, ragContext);
        ctx.state.put("useCaseModel", useCasePlantUml);
        ctx.log("usecase.generate: " + useCasePlantUml.length() + " chars");
    }
}

