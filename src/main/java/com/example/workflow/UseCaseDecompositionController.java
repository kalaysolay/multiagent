package com.example.workflow;

import com.example.portal.agents.iconix.entity.UseCaseScenario;
import com.example.portal.agents.iconix.service.UseCaseScenarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
     * Получить все декомпозированные сценарии для workflow сессии.
     * 
     * GET /api/usecase/decomposition/{requestId}
     */
    @GetMapping("/{requestId}")
    public ResponseEntity<ScenariosResponse> getScenarios(@PathVariable String requestId) {
        try {
            List<UseCaseScenario> scenarios = scenarioService.getScenariosByRequestId(requestId);
            
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
            log.error("Error getting scenarios for requestId: {}", requestId, e);
            return ResponseEntity.internalServerError()
                    .body(new ScenariosResponse(List.of()));
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
    
    public record ScenarioDto(
            String id,
            String useCaseAlias,
            String useCaseName,
            String scenarioContent,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {}
}

