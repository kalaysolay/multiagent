package com.example.workflow;

import java.util.Map;

public record PlanStep(String tool, Map<String, Object> args) {}
