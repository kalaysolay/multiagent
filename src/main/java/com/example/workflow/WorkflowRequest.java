package com.example.workflow;

//public record WorkflowRequest(String narrative) {}

import java.util.List;
import java.util.Map;

public record WorkflowRequest(
        String narrative,
        String goal,
        String task,
        Constraints constraints
)
{
    public record Constraints(Integer maxIterations) {}
}


