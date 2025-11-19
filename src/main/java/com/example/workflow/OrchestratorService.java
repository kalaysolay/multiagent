package com.example.workflow;

import lombok.RequiredArgsConstructor;
import com.example.workflow.AiConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class OrchestratorService {
    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private final ChatClient chat;                 // Spring AI
    private final WorkersRegistry registry;

    @Value("classpath:prompts/orchestrator_plan.st")
    private Resource orchestratorPrompt;

    public WorkflowResponse run(WorkflowRequest req) throws Exception {
        String requestId = UUID.randomUUID().toString();
        var ctx = new Worker.Context(requestId,
                Optional.ofNullable(req.narrative()).orElse(""),
                Optional.ofNullable(req.goal()).orElse(""));
        int maxIter = Optional.ofNullable(req.constraints())
                .map(WorkflowRequest.Constraints::maxIterations).orElse(6);

        log.info("=== Запуск оркестратора ИИ-агента ===");
        log.info("Request ID: {}", requestId);
        log.info("Цель: {}", ctx.goal);
        log.info("Контекст (narrative): {}", ctx.narrative);
        log.info("Максимальное количество итераций: {}", maxIter);

        // 1) Получить план от LLM или сгенерировать детерминированно
        OrchestratorPlan plan;
        if (shouldForceNarrativeReview(ctx.narrative, ctx.goal)) {
            plan = new OrchestratorPlan(
                    "Пользователь запросил ревью только нарратива, пропускаем построение модели.",
                    List.of(new PlanStep("review", Map.of("target", "narrative"))));
            ctx.log("plan.override: narrative-only");
            log.info("Цель интерпретирована как ревью только нарратива. План сгенерирован детерминированно.");
        } else {
            plan = chat.prompt()
                    .user(u -> u.text(read(orchestratorPrompt))
                            .param("narrative", ctx.narrative)
                            .param("goal", ctx.goal))
                    .call()
                    .entity(OrchestratorPlan.class);
        }

        ctx.log("plan: " + plan.plan().size() + " steps");
        log.info("План получен. Количество шагов: {}", plan.plan().size());

        // 2) Исполнить шаги
        int iter = 0;
        for (var step : plan.plan()) {
            if (++iter > maxIter) { ctx.log("maxIterations reached"); break; }

            log.info("=== Выполнение шага {}/{} ===", iter, Math.min(maxIter, plan.plan().size()));
            log.info("Инструмент: '{}'", step.tool());
            log.info("Параметры: {}", step.args() != null ? step.args() : "не заданы");

            var worker = registry.get(step.tool());
            log.info("Запуск worker'а: {}", worker.getClass().getSimpleName());
            worker.execute(ctx, step.args() == null ? Map.of() : step.args());
            log.info("Шаг {} успешно выполнен.", iter);
        }

        // 3) Сформировать ответ
        Map<String,Object> artifacts = new LinkedHashMap<>();
        if (ctx.state.containsKey("plantuml")) artifacts.put("plantuml", ctx.state.get("plantuml"));
        if (ctx.state.containsKey("issues")) artifacts.put("issues", ctx.state.get("issues"));
        if (ctx.state.containsKey("narrativeIssues")) artifacts.put("narrativeIssues", ctx.state.get("narrativeIssues"));
        log.info("Оркестратор завершил выполнение. Request ID: {}", requestId);
        return new WorkflowResponse(requestId, plan, artifacts, ctx.logs);
    }

    private static String read(Resource r){
        try (var in = r.getInputStream()) { return new String(in.readAllBytes()); }
        catch (Exception e){ throw new RuntimeException(e); }
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
