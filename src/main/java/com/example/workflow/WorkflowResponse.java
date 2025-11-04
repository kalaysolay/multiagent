package com.example.workflow;

import java.util.List;
import java.util.Map;

//public record WorkflowResponse(String plantUmlFinal, List<Issue> addressedIssues) {}

public record WorkflowResponse(
        String requestId,
        OrchestratorPlan orchestrator,
        Map<String, Object> artifacts,
        List<String> logs
) {}