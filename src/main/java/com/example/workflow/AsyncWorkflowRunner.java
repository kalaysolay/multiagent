package com.example.workflow;

import com.example.portal.agents.iconix.model.ResumeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Запуск run/resume в фоне, чтобы контроллер мог сразу вернуть 202 и клиент — опрашивать сессию.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncWorkflowRunner {

    private final OrchestratorService orchestrator;

    @Async
    public void runAsync(String requestId) {
        try {
            orchestrator.runAsync(requestId);
        } catch (Exception e) {
            log.error("Async run failed for requestId: {}", requestId, e);
        }
    }

    @Async
    public void resumeAsync(String requestId, ResumeRequest req) {
        try {
            orchestrator.resumeWorkflow(requestId, req);
        } catch (Exception e) {
            log.error("Async resume failed for requestId: {}", requestId, e);
        }
    }
}
