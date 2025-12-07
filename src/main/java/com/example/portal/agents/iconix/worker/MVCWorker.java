package com.example.portal.agents.iconix.worker;

import com.example.portal.agents.iconix.service.agentservices.MVCModellerService;
import com.example.portal.shared.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MVCWorker implements Worker {

    private final MVCModellerService mvcModeller;
    private final RagService ragService;

    @Override
    public String name() {
        return "mvc";
    }

    @Override
    public void execute(Context ctx, Map<String, Object> args) {
        String narrative = ctx.narrativeEffective();
        String domainModel = (String) ctx.state.get("plantuml");
        String useCaseModel = (String) ctx.state.get("useCaseModel");
        
        if (domainModel == null || domainModel.isBlank()) {
            throw new IllegalStateException("No domain model (plantuml) in context; run model first.");
        }
        
        if (useCaseModel == null || useCaseModel.isBlank()) {
            throw new IllegalStateException("No use case model (useCaseModel) in context; run usecase first.");
        }

        var rag = ragService.retrieveContext(narrative, 4);
        ctx.log(String.format("rag.mvc: fragments=%d, vs=%s",
                rag.fragmentsCount(),
                rag.vectorStoreAvailable()));
        String ragContext = rag.text();

        String mvcPlantUml = mvcModeller.generateMVCPlantUml(narrative, domainModel, useCaseModel, ragContext);
        ctx.state.put("mvcDiagram", mvcPlantUml);
        ctx.log("mvc.generate: " + mvcPlantUml.length() + " chars");
    }
}

