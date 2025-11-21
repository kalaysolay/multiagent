package com.example.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface Worker {
    String name(); // "model" | "review"
    void execute(Context ctx, Map<String, Object> args) throws Exception;

    // Общий контекст пайплайна
    final class Context {
        public final String requestId;
        public final String narrative;
        public final String goal;
        public final String task;
        public final Map<String, Object> state = new HashMap<>(); // plantuml, issues, и т.д.
        public final List<String> logs = new ArrayList<>();

        public Context(String requestId, String narrative, String goal, String task) {
            this.requestId = requestId;
            this.narrative = narrative == null ? "" : narrative;
            this.goal = goal == null ? "" : goal;
            this.task = task == null ? "" : task;
        }

        public void log(String s){ logs.add(s); }

        public void overrideNarrative(String narrativeOverride) {
            if (narrativeOverride != null && !narrativeOverride.isBlank()) {
                state.put("narrativeOverride", narrativeOverride);
            }
        }

        public String narrativeEffective() {
            Object override = state.get("narrativeOverride");
            if (override instanceof String ov && !ov.isBlank()) return ov;
            return narrative;
        }

        public String taskOrGoal() {
            if (task != null && !task.isBlank()) return task;
            return goal;
        }
    }
}
