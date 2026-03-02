package com.example.workflow;

import com.example.portal.agents.iconix.model.OrchestratorPlan;
import com.example.portal.agents.iconix.model.ResumeRequest;
import com.example.portal.agents.iconix.model.WorkflowRequest;
import com.example.portal.agents.iconix.model.WorkflowResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {
    private final OrchestratorService orchestrator;
    private final WorkflowSessionService sessionService;
    private final AsyncWorkflowRunner asyncRunner;

    @GetMapping("/")
    public String index() {
        return "redirect:/index.html";
    }

    /**
     * Запуск workflow: сразу возвращает 202 и requestId, выполнение идёт в фоне.
     * Клиент опрашивает GET /session/{requestId} для обновления пайплайна и артефактов.
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> run(@RequestBody WorkflowRequest req) {
        String requestId = req.requestId() != null && !req.requestId().isBlank()
                ? req.requestId()
                : UUID.randomUUID().toString();
        String goal = req.goal() != null ? req.goal().trim() : "";
        OrchestratorPlan plan = orchestrator.getDefaultPlan();
        sessionService.createSessionForRun(requestId, goal, plan);
        asyncRunner.runAsync(requestId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("requestId", requestId));
    }

    /**
     * Возобновление workflow: сразу возвращает 202, выполнение идёт в фоне.
     * Клиент опрашивает GET /session/{requestId}.
     */
    @PostMapping("/resume")
    public ResponseEntity<Map<String, String>> resume(@RequestBody ResumeRequest req) {
        if (req.requestId() == null || req.requestId().isBlank()) {
            throw new IllegalArgumentException("requestId is required for resume");
        }
        asyncRunner.resumeAsync(req.requestId(), req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("requestId", req.requestId()));
    }
    
    @GetMapping("/sessions")
    public java.util.List<WorkflowSessionSummary> getAllSessions() {
        return sessionService.getAllSessions();
    }
    
    @GetMapping("/session/{requestId}")
    public WorkflowResponse getSession(@PathVariable String requestId) {
        return sessionService.getSessionData(requestId);
    }
}
