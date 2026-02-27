package com.example.workflow;

import com.example.portal.agents.iconix.exception.PauseForUserReviewException;
import com.example.portal.agents.iconix.model.OrchestratorPlan;
import com.example.portal.agents.iconix.model.PlanStep;
import com.example.portal.agents.iconix.model.ResumeRequest;
import com.example.portal.agents.iconix.model.WorkflowRequest;
import com.example.portal.agents.iconix.model.WorkflowResponse;
import com.example.portal.agents.iconix.worker.Worker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class OrchestratorService {
    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private final com.example.portal.agents.iconix.service.WorkersRegistry registry;
    private final WorkflowSessionService sessionService;

    @Transactional
    public WorkflowResponse run(WorkflowRequest req) throws Exception {
        String requestId = req.requestId() != null ? req.requestId() : UUID.randomUUID().toString();
        
        // Запуск только для новой сессии; если сессия в паузе — возобновлять через POST /resume
        Optional<com.example.portal.agents.iconix.entity.WorkflowSession> existingSession = sessionService.loadSession(requestId);
        if (existingSession.isPresent() && existingSession.get().getStatus() == com.example.portal.agents.iconix.model.WorkflowStatus.PAUSED_FOR_REVIEW) {
            throw new IllegalStateException(
                    "Сессия " + requestId + " приостановлена для ревью. Используйте POST /workflow/resume с requestId, narrative и domainModel.");
        }
        
        // Создаем новую сессию: ввод пользователя — только goal
        String goal = Optional.ofNullable(req.goal()).orElse("").trim();
        log.info("=== Запуск оркестратора ===");
        log.info("Request ID: {}", requestId);
        log.info("Incoming goal length: {} chars, preview: {}", goal.length(), goal.length() > 0 ? goal.substring(0, Math.min(100, goal.length())) + (goal.length() > 100 ? "..." : "") : "(empty)");

        var ctx = new Worker.Context(requestId, "", goal);
        // log.info("Максимальное количество итераций: {}", maxIter);

        // 1) Сформировать план: narrative → userReview → модели (качественный нарратив до построения моделей)
        OrchestratorPlan plan = buildDefaultPlan();
        ctx.log("plan: Narrative → UserReview → Model → Review → Model(refine) → UseCase → MVC → Scenario");
        log.info("План: Narrative → UserReview → Model → Review → Model(refine) → UseCase → MVC → Scenario. Шагов: {}", plan.plan().size());

        // 2) Исполнить шаги
        return executeSteps(ctx, plan, requestId);
    }
    
    @Transactional
    public WorkflowResponse resumeWorkflow(String requestId, ResumeRequest req) throws Exception {
        log.info("=== Возобновление workflow ===");
        log.info("Request ID: {}", requestId);
        
        Optional<com.example.portal.agents.iconix.entity.WorkflowSession> sessionOpt = sessionService.loadSession(requestId);
        if (sessionOpt.isEmpty()) {
            throw new IllegalArgumentException("Session not found: " + requestId);
        }
        
        com.example.portal.agents.iconix.entity.WorkflowSession session = sessionOpt.get();
        if (session.getStatus() != com.example.portal.agents.iconix.model.WorkflowStatus.PAUSED_FOR_REVIEW) {
            throw new IllegalStateException("Session is not paused for review: " + requestId);
        }
        
        // Обновляем контекст с данными пользователя (отредактированный нарратив и/или доменная модель)
        if (req.narrative() != null && !req.narrative().isBlank()) {
            sessionService.updateContextFromUserInput(requestId, req.narrative(), null);
        }
        if (req.domainModel() != null && !req.domainModel().isBlank()) {
            sessionService.updateContextFromUserInput(requestId, null, req.domainModel());
        }
        
        // Перезагружаем сессию из БД, чтобы контекст содержал сохранённые narrative/domainModel
        session = sessionService.loadSession(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + requestId));
        
        // Восстанавливаем контекст и план
        Worker.Context ctx = sessionService.restoreContext(session);
        OrchestratorPlan plan = sessionService.restorePlan(session);
        int currentStepIndex = session.getCurrentStepIndex();
        // int maxIter = session.getMaxIterations() != null ? session.getMaxIterations() : 6;
        
        log.info("Возобновление с шага {}/{}", currentStepIndex + 1, plan.plan().size());
        
        // Продолжаем выполнение с текущего шага
        return executeStepsFromIndex(ctx, plan, currentStepIndex + 1, requestId);
    }
    
    private WorkflowResponse executeSteps(Worker.Context ctx, OrchestratorPlan plan, 
                                         String requestId) throws Exception {
        return executeStepsFromIndex(ctx, plan, 0, requestId);
    }
    
    private WorkflowResponse executeStepsFromIndex(Worker.Context ctx, OrchestratorPlan plan,
                                                   int startIndex, String requestId) throws Exception {
        List<PlanStep> steps = plan.plan();
        int currentStepIndex = startIndex;
        
        try {
            for (int i = startIndex; i < steps.size(); i++) {
                currentStepIndex = i;
                
                PlanStep step = steps.get(i);
                
                log.info("=== Выполнение шага {}/{} ===", i + 1, steps.size());
                log.info("Инструмент: '{}'", step.tool());
                log.info("Параметры: {}", step.args() != null ? step.args() : "не заданы");
                
                // Сохраняем сессию перед каждым шагом
                sessionService.saveSession(ctx, plan, i, com.example.portal.agents.iconix.model.WorkflowStatus.RUNNING, null, null);
                
                var worker = registry.get(step.tool());
                log.info("Запуск worker'а: {}", worker.getClass().getSimpleName());
                
                try {
                    worker.execute(ctx, step.args() == null ? Map.of() : step.args());
                    log.info("Шаг {} успешно выполнен.", i + 1);
                } catch (PauseForUserReviewException e) {
                    // Пауза для пользовательского ревью
                    log.info("Workflow приостановлен для пользовательского ревью на шаге {}", i + 1);
                    sessionService.saveSession(ctx, plan, i, com.example.portal.agents.iconix.model.WorkflowStatus.PAUSED_FOR_REVIEW, 
                                             e.getReviewData(), null);
                    
                    Map<String, Object> artifacts = sessionService.buildCoreArtifacts(ctx.goal, ctx.narrativeEffective(), ctx.state);
                    artifacts.put("_status", "PAUSED_FOR_REVIEW");
                    artifacts.put("_reviewData", new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(e.getReviewData(), Map.class));
                    
                    return new WorkflowResponse(requestId, plan, artifacts, ctx.logs);
                }
            }
            
            // Все шаги выполнены
            sessionService.saveSession(ctx, plan, currentStepIndex, com.example.portal.agents.iconix.model.WorkflowStatus.COMPLETED, null, null);
            
            Map<String, Object> artifacts = sessionService.buildCoreArtifacts(ctx.goal, ctx.narrativeEffective(), ctx.state);
            artifacts.put("_status", "COMPLETED");
            log.info("Оркестратор завершил выполнение. Request ID: {}", requestId);
            return new WorkflowResponse(requestId, plan, artifacts, ctx.logs);
            
        } catch (Exception e) {
            log.error("Ошибка выполнения workflow", e);
            sessionService.saveSession(ctx, plan, currentStepIndex, com.example.portal.agents.iconix.model.WorkflowStatus.FAILED, null, null);
            throw e;
        }
    }
    
    /**
     * План по умолчанию: сначала нарратив и ревью пользователем, затем модели.
     * UserReview сразу после narrative нужен, чтобы на вход model попадал уже согласованный нарратив.
     */
    private OrchestratorPlan buildDefaultPlan() {
        List<PlanStep> steps = List.of(
                new PlanStep("narrative", Map.of()),
                new PlanStep("userReview", Map.of()), // Пауза: пользователь проверяет/правит нарратив (доменной модели ещё нет)
                new PlanStep("model", Map.of("mode", "generate")),
                new PlanStep("review", Map.of("target", "model")),
                new PlanStep("model", Map.of("mode", "refine")),
                new PlanStep("usecase", Map.of()),
                new PlanStep("mvc", Map.of()),
                new PlanStep("scenario", Map.of())
        );
        return new OrchestratorPlan(
                "Narrative → UserReview → Model → Review → Model(refine) → UseCase → MVC → Scenario. Ревью нарратива до построения моделей.",
                steps
        );
    }
}
