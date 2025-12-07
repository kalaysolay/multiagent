package com.example.portal.agents.iconix.model;

public record WorkflowRequest(
        String requestId,      // Для возобновления существующей сессии
        String narrative,      // Нарратив (может быть обновлен при возобновлении)
        String goal,
        String task,
        String domainModel    // Domain model (PlantUML) для обновления при возобновлении
)
{}

