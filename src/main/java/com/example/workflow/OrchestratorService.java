package com.example.workflow;

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

    private final WorkersRegistry registry;
    private final WorkflowSessionService sessionService;

    @Transactional
    public WorkflowResponse run(WorkflowRequest req) throws Exception {
        String requestId = req.requestId() != null ? req.requestId() : UUID.randomUUID().toString();
        
        // Проверяем, нужно ли возобновить существующую сессию
        Optional<WorkflowSession> existingSession = sessionService.loadSession(requestId);
        if (existingSession.isPresent() && existingSession.get().getStatus() == WorkflowStatus.PAUSED_FOR_REVIEW) {
            return resumeWorkflow(requestId, req);
        }
        
        // Создаем новую сессию
        var ctx = new Worker.Context(requestId,
                Optional.ofNullable(req.narrative()).orElse(""),
                Optional.ofNullable(req.goal()).orElse(""),
                Optional.ofNullable(req.task()).orElse(""));
        // int maxIter = Optional.ofNullable(req.constraints())
        //         .map(WorkflowRequest.Constraints::maxIterations).orElse(6);

        log.info("=== Запуск оркестратора ИИ-агента ===");
        log.info("Request ID: {}", requestId);
        log.info("Цель: {}", ctx.goal);
        log.info("Описание задачи (task): {}", ctx.task);
        log.info("Контекст (narrative): {}", ctx.narrative);
        // log.info("Максимальное количество итераций: {}", maxIter);

        // 1) Сформировать план
        OrchestratorPlan plan;
        if (shouldForceNarrativeReview(ctx.narrative, ctx.goal)) {
            plan = new OrchestratorPlan(
                    "Пользователь запросил ревью только нарратива, пропускаем построение модели.",
                    List.of(new PlanStep("review", Map.of("target", "narrative"))));
            ctx.log("plan.override: narrative-only");
            log.info("Цель интерпретирована как ревью только нарратива. План сгенерирован детерминированно.");
        } else {
            plan = buildDefaultPlan();
            ctx.log("plan.default: narrative→model→review→refine→userReview→usecase→mvc");
            log.info("Используем стандартный план: Narrative → Model → Review → Model(refine) → UserReview → UseCase → MVC.");
        }

        ctx.log("plan: " + plan.plan().size() + " steps");
        log.info("План получен. Количество шагов: {}", plan.plan().size());

        // 2) Исполнить шаги
        return executeSteps(ctx, plan, requestId);
    }
    
    @Transactional
    public WorkflowResponse resumeWorkflow(String requestId, WorkflowRequest req) throws Exception {
        log.info("=== Возобновление workflow ===");
        log.info("Request ID: {}", requestId);
        
        Optional<WorkflowSession> sessionOpt = sessionService.loadSession(requestId);
        if (sessionOpt.isEmpty()) {
            throw new IllegalArgumentException("Session not found: " + requestId);
        }
        
        WorkflowSession session = sessionOpt.get();
        if (session.getStatus() != WorkflowStatus.PAUSED_FOR_REVIEW) {
            throw new IllegalStateException("Session is not paused for review: " + requestId);
        }
        
        // Обновляем контекст с данными пользователя
        if (req.narrative() != null && !req.narrative().isBlank()) {
            sessionService.updateContextFromUserInput(requestId, req.narrative(), null);
        }
        if (req.domainModel() != null && !req.domainModel().isBlank()) {
            sessionService.updateContextFromUserInput(requestId, null, req.domainModel());
        }
        
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
                sessionService.saveSession(ctx, plan, i, WorkflowStatus.RUNNING, null, null);
                
                var worker = registry.get(step.tool());
                log.info("Запуск worker'а: {}", worker.getClass().getSimpleName());
                
                try {
                    worker.execute(ctx, step.args() == null ? Map.of() : step.args());
                    log.info("Шаг {} успешно выполнен.", i + 1);
                } catch (PauseForUserReviewException e) {
                    // Пауза для пользовательского ревью
                    log.info("Workflow приостановлен для пользовательского ревью на шаге {}", i + 1);
                    sessionService.saveSession(ctx, plan, i, WorkflowStatus.PAUSED_FOR_REVIEW, 
                                             e.getReviewData(), null);
                    
                    // Формируем ответ с информацией о паузе
                    Map<String, Object> artifacts = buildArtifacts(ctx);
                    artifacts.put("_status", "PAUSED_FOR_REVIEW");
                    artifacts.put("_reviewData", new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(e.getReviewData(), Map.class));
                    
                    return new WorkflowResponse(requestId, plan, artifacts, ctx.logs);
                }
            }
            
            // Все шаги выполнены
            sessionService.saveSession(ctx, plan, currentStepIndex, WorkflowStatus.COMPLETED, null, null);
            
            // Формируем финальный ответ
            Map<String, Object> artifacts = buildArtifacts(ctx);
            artifacts.put("_status", "COMPLETED");
            log.info("Оркестратор завершил выполнение. Request ID: {}", requestId);
            return new WorkflowResponse(requestId, plan, artifacts, ctx.logs);
            
        } catch (Exception e) {
            log.error("Ошибка выполнения workflow", e);
            sessionService.saveSession(ctx, plan, currentStepIndex, WorkflowStatus.FAILED, null, null);
            throw e;
        }
    }
    
    private Map<String, Object> buildArtifacts(Worker.Context ctx) {
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("narrative", ctx.narrativeEffective());
        if (ctx.state.containsKey("plantuml")) artifacts.put("plantuml", ctx.state.get("plantuml"));
        if (ctx.state.containsKey("issues")) artifacts.put("issues", ctx.state.get("issues"));
        if (ctx.state.containsKey("narrativeIssues")) artifacts.put("narrativeIssues", ctx.state.get("narrativeIssues"));
        if (ctx.state.containsKey("useCaseModel")) artifacts.put("useCaseModel", ctx.state.get("useCaseModel"));
        if (ctx.state.containsKey("mvcDiagram")) artifacts.put("mvcDiagram", ctx.state.get("mvcDiagram"));
        return artifacts;
    }

    private OrchestratorPlan buildDefaultPlan() {
        List<PlanStep> steps = List.of(
                new PlanStep("narrative", Map.of()),
                new PlanStep("model", Map.of("mode", "generate")),
                new PlanStep("review", Map.of("target", "model")),
                new PlanStep("model", Map.of("mode", "refine")),
                new PlanStep("userReview", Map.of()), // Пауза для пользовательского ревью
                new PlanStep("usecase", Map.of()),
                new PlanStep("mvc", Map.of())
        );
        return new OrchestratorPlan(
                "Стандартный мультиагентский цикл: Narrative → Model → Review → Model(refine) → UserReview → UseCase → MVC.",
                steps
        );
    }

    private static boolean shouldForceNarrativeReview(String narrative, String goal) {
        String combined = (goal == null ? "" : goal) + " " + (narrative == null ? "" : narrative);
        String text = combined.toLowerCase();

        boolean mentionsNarrative = text.contains("нарратив") || text.contains("narrative");
        if (!mentionsNarrative) return false;

        boolean mentionsReview = text.contains("ревью") || text.contains("review")
                || text.contains("провер") || text.contains("оцен");
        if (!mentionsReview) return false;

        boolean mentionsModel = text.contains("модель") || text.contains("model")
                || text.contains("plantuml") || text.contains("iconix");

        boolean explicitNarrativeOnly = text.contains("только нарратив") || text.contains("без модели")
                || text.contains("narrative only");

        return explicitNarrativeOnly || (!mentionsModel);
    }
}
