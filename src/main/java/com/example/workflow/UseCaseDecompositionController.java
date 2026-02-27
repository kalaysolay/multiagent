package com.example.workflow;

import com.example.portal.agents.iconix.entity.UseCaseMvc;
import com.example.portal.agents.iconix.entity.UseCaseScenario;
import com.example.portal.agents.iconix.service.UseCaseMvcService;
import com.example.portal.agents.iconix.service.UseCaseScenarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Контроллер для декомпозиции Use Case.
 * Обрабатывает запросы на декомпозицию отдельных Use Case и возвращает сгенерированные сценарии.
 */
@RestController
@RequestMapping("/api/usecase/decomposition")
@RequiredArgsConstructor
@Slf4j
public class UseCaseDecompositionController {
    
    private final UseCaseDecompositionService decompositionService;
    private final UseCaseScenarioService scenarioService;
    private final UseCaseMvcService mvcService;
    
    /**
     * Декомпозировать выбранные Use Case.
     * 
     * POST /api/usecase/decomposition
     * Body: {
     *   "requestId": "abc-123",
     *   "useCases": [
     *     { "alias": "reviewGiftByOfficer", "name": "Проверить подарок офицером" }
     *   ]
     * }
     */
    @PostMapping
    public ResponseEntity<DecompositionResponse> decompose(@RequestBody DecompositionRequest request) {
        try {
            log.info("Decomposition request: requestId={}, useCases={}", 
                    request.requestId(), request.useCases().size());
            
            List<DecompositionResult> results = request.useCases().stream()
                    .map(uc -> {
                        try {
                            String scenario = decompositionService.decomposeUseCase(
                                    request.requestId(),
                                    uc.alias(),
                                    uc.name()
                            );
                            return new DecompositionResult(
                                    uc.alias(),
                                    uc.name(),
                                    scenario,
                                    true,
                                    null
                            );
                        } catch (Exception e) {
                            log.error("Failed to decompose Use Case: {} ({})", uc.name(), uc.alias(), e);
                            return new DecompositionResult(
                                    uc.alias(),
                                    uc.name(),
                                    null,
                                    false,
                                    e.getMessage()
                            );
                        }
                    })
                    .toList();
            
            return ResponseEntity.ok(new DecompositionResponse(results));
            
        } catch (Exception e) {
            log.error("Error processing decomposition request", e);
            return ResponseEntity.internalServerError()
                    .body(new DecompositionResponse(List.of()));
        }
    }
    
    /**
     * Получить декомпозированные сценарии и MVC-диаграммы для workflow сессии.
     *
     * GET /api/usecase/decomposition/{requestId}
     * Response: { scenarios: ScenarioDto[], mvcDiagrams: MvcDiagramDto[] }
     */
    @GetMapping("/{requestId}")
    public ResponseEntity<DecompositionArtifactsResponse> getArtifacts(@PathVariable String requestId) {
        try {
            List<UseCaseScenario> scenarios = scenarioService.getScenariosByRequestId(requestId);
            List<UseCaseMvc> mvcList = mvcService.getByRequestId(requestId);

            List<ScenarioDto> scenarioDtos = scenarios.stream()
                    .map(s -> new ScenarioDto(
                            s.getId().toString(),
                            s.getUseCaseAlias(),
                            s.getUseCaseName(),
                            s.getScenarioContent(),
                            s.getCreatedAt(),
                            s.getUpdatedAt()
                    ))
                    .toList();

            List<MvcDiagramDto> mvcDtos = mvcList.stream()
                    .map(m -> new MvcDiagramDto(
                            m.getId().toString(),
                            m.getUseCaseAlias(),
                            m.getUseCaseName(),
                            m.getMvcPlantuml(),
                            m.getCreatedAt(),
                            m.getUpdatedAt()
                    ))
                    .toList();

            return ResponseEntity.ok(new DecompositionArtifactsResponse(scenarioDtos, mvcDtos));

        } catch (Exception e) {
            log.error("Error getting artifacts for requestId: {}", requestId, e);
            return ResponseEntity.internalServerError()
                    .body(new DecompositionArtifactsResponse(List.of(), List.of()));
        }
    }
    
    /**
     * Получить сценарии для конкретного Use Case.
     * 
     * GET /api/usecase/decomposition/{requestId}/{useCaseAlias}
     */
    @GetMapping("/{requestId}/{useCaseAlias}")
    public ResponseEntity<ScenariosResponse> getScenariosByAlias(
            @PathVariable String requestId,
            @PathVariable String useCaseAlias) {
        try {
            List<UseCaseScenario> scenarios = scenarioService.getScenariosByRequestIdAndAlias(
                    requestId, useCaseAlias);
            
            List<ScenarioDto> scenarioDtos = scenarios.stream()
                    .map(s -> new ScenarioDto(
                            s.getId().toString(),
                            s.getUseCaseAlias(),
                            s.getUseCaseName(),
                            s.getScenarioContent(),
                            s.getCreatedAt(),
                            s.getUpdatedAt()
                    ))
                    .toList();
            
            return ResponseEntity.ok(new ScenariosResponse(scenarioDtos));
            
        } catch (Exception e) {
            log.error("Error getting scenarios for requestId: {}, alias: {}", requestId, useCaseAlias, e);
            return ResponseEntity.internalServerError()
                    .body(new ScenariosResponse(List.of()));
        }
    }
    
    // DTOs
    public record DecompositionRequest(
            String requestId,
            List<UseCaseInfo> useCases
    ) {}
    
    public record UseCaseInfo(
            String alias,
            String name
    ) {}
    
    public record DecompositionResponse(
            List<DecompositionResult> results
    ) {}
    
    public record DecompositionResult(
            String useCaseAlias,
            String useCaseName,
            String scenario,
            boolean success,
            String error
    ) {}
    
    public record ScenariosResponse(
            List<ScenarioDto> scenarios
    ) {}

    /** Ответ GET /decomposition/{requestId}: сценарии и MVC-диаграммы. */
    public record DecompositionArtifactsResponse(
            List<ScenarioDto> scenarios,
            List<MvcDiagramDto> mvcDiagrams
    ) {}

    public record ScenarioDto(
            String id,
            String useCaseAlias,
            String useCaseName,
            String scenarioContent,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {}

    public record MvcDiagramDto(
            String id,
            String useCaseAlias,
            String useCaseName,
            String mvcPlantuml,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {}
}

