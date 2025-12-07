package com.example.portal.agents.iconix.model;

import java.util.List;
import java.util.Map;

public record WorkflowResponse(
        String requestId,
        OrchestratorPlan orchestrator,
        Map<String, Object> artifacts,
        List<String> logs
) {}

