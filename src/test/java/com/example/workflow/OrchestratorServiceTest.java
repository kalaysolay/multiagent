package com.example.workflow;

import com.example.portal.agents.iconix.exception.PauseForUserReviewException;
import com.example.portal.agents.iconix.model.OrchestratorPlan;
import com.example.portal.agents.iconix.model.PlanStep;
import com.example.portal.agents.iconix.model.WorkflowRequest;
import com.example.portal.agents.iconix.model.WorkflowResponse;
import com.example.portal.agents.iconix.service.WorkersRegistry;
import com.example.portal.agents.iconix.worker.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Тесты оркестратора: порядок шагов плана, пауза на userReview без доменной модели,
 * отсутствие ветки «только нарратив».
 */
@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

    private static final List<String> EXPECTED_STEP_ORDER = List.of(
            "narrative",
            "userReview",
            "model",
            "review",
            "model",
            "usecase",
            "mvc",
            "scenario"
    );

    @Mock
    private WorkersRegistry registry;

    @Mock
    private WorkflowSessionService sessionService;

    @InjectMocks
    private OrchestratorService orchestratorService;

    /** Воркер narrative: только устанавливает нарратив в контекст. */
    private Worker narrativeWorker;

    /** Воркер userReview: бросает паузу для ревью. */
    private Worker userReviewWorker;

    @BeforeEach
    void setUp() {
        narrativeWorker = new Worker() {
            @Override
            public String name() { return "narrative"; }
            @Override
            public void execute(Worker.Context ctx, Map<String, Object> args) {
                ctx.overrideNarrative("test narrative");
            }
        };
        userReviewWorker = new Worker() {
            @Override
            public String name() { return "userReview"; }
            @Override
            public void execute(Worker.Context ctx, Map<String, Object> args) {
                throw new PauseForUserReviewException(
                        ctx.requestId,
                        "{\"narrative\":\"test narrative\"}",
                        "Pause for user review");
            }
        };

        when(sessionService.loadSession(any())).thenReturn(Optional.empty());
        when(registry.get("narrative")).thenReturn(narrativeWorker);
        when(registry.get("userReview")).thenReturn(userReviewWorker);
    }

    @Test
    @DisplayName("План по умолчанию содержит 8 шагов: narrative → userReview → model → review → model → usecase → mvc → scenario")
    void defaultPlan_hasEightStepsInCorrectOrder() throws Exception {
        WorkflowRequest req = new WorkflowRequest(null, "Опиши систему заказов");
        WorkflowResponse response = orchestratorService.run(req);

        List<PlanStep> steps = response.orchestrator().plan();
        assertThat(steps).hasSize(8);

        for (int i = 0; i < EXPECTED_STEP_ORDER.size(); i++) {
            assertThat(steps.get(i).tool()).isEqualTo(EXPECTED_STEP_ORDER.get(i));
        }

        assertThat(steps.get(2).args()).containsEntry("mode", "generate");
        assertThat(steps.get(3).args()).containsEntry("target", "model");
        assertThat(steps.get(4).args()).containsEntry("mode", "refine");
    }

    @Test
    @DisplayName("При цели «только нарратив ревью» план всё равно полный, первый шаг — narrative")
    void goalNarrativeOnly_stillUsesFullPlanFirstStepIsNarrative() throws Exception {
        WorkflowRequest req = new WorkflowRequest(null, "только нарратив ревью");
        WorkflowResponse response = orchestratorService.run(req);

        List<PlanStep> steps = response.orchestrator().plan();
        assertThat(steps).hasSize(8);
        assertThat(steps.get(0).tool()).isEqualTo("narrative");
        assertThat(steps.get(1).tool()).isEqualTo("userReview");
    }

    @Test
    @DisplayName("Первый проход останавливается на userReview; в артефактах есть narrative, нет доменной модели")
    void firstPass_pausesAtUserReview_artifactsHaveNarrativeNoDomainModel() throws Exception {
        WorkflowRequest req = new WorkflowRequest(null, "Цель");
        WorkflowResponse response = orchestratorService.run(req);

        assertThat(response.artifacts()).containsKey("_status");
        assertThat(response.artifacts().get("_status")).isEqualTo("PAUSED_FOR_REVIEW");
        assertThat(response.artifacts()).containsKey("narrative");
        assertThat(response.artifacts().get("narrative")).isEqualTo("test narrative");
        assertThat(response.artifacts()).doesNotContainKey("plantuml");
    }
}
