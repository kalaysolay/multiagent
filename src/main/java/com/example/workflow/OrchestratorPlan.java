package com.example.workflow;

import java.util.List;

public record OrchestratorPlan(String analysis, List<PlanStep> plan) {}
