package com.example.workflow;

//public record WorkflowRequest(String narrative) {}

import java.util.List;
import java.util.Map;

public record WorkflowRequest(
        String requestId,      // Для возобновления существующей сессии
        String narrative,      // Нарратив (может быть обновлен при возобновлении)
        String goal,
        String task,
        String domainModel    // Domain model (PlantUML) для обновления при возобновлении
        // Constraints constraints  // Закомментировано - рудимент
)
{
    // public record Constraints(Integer maxIterations) {}  // Закомментировано - рудимент
}


