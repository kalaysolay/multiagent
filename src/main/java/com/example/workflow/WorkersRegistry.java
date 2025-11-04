package com.example.workflow;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkersRegistry {
    private final List<Worker> workers; // Spring соберёт все реализации
    private final Map<String, Worker> byName = new HashMap<>();
    @PostConstruct
    void init(){ for (var w: workers) byName.put(w.name(), w); }
    public Worker get(String tool){
        var w = byName.get(tool);
        if (w == null) throw new IllegalArgumentException("Unknown tool: " + tool);
        return w;
    }
}
