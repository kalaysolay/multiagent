package com.example.workflow;

import com.example.portal.agents.iconix.service.UseCaseScenarioService;
import com.example.portal.agents.iconix.service.agentservices.ScenarioWriterService;
import com.example.portal.shared.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для декомпозиции отдельных Use Case.
 * Генерирует сценарий для конкретного Use Case и сохраняет его в БД.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UseCaseDecompositionService {
    
    private final ScenarioWriterService scenarioWriter;
    private final RagService ragService;
    private final UseCaseScenarioService scenarioService;
    private final WorkflowSessionService workflowSessionService;
    private final PlantUmlFilter plantUmlFilter;
    
    /**
     * Декомпозировать Use Case - сгенерировать сценарий для конкретного Use Case.
     * 
     * @param requestId ID workflow сессии
     * @param useCaseAlias Алиас Use Case (например, "reviewGiftByOfficer")
     * @param useCaseName Название Use Case (например, "Проверить подарок офицером")
     * @return Сгенерированный сценарий
     */
    @Transactional
    public String decomposeUseCase(String requestId, String useCaseAlias, String useCaseName) {
        log.info("Starting decomposition for Use Case: {} (alias: {}), requestId: {}", 
                useCaseName, useCaseAlias, requestId);
        
        try {
            // Получаем данные из workflow сессии
            var sessionData = workflowSessionService.getSessionData(requestId);
            if (sessionData == null) {
                throw new IllegalArgumentException("Workflow session not found: " + requestId);
            }
            
            var artifacts = sessionData.artifacts();
            if (artifacts == null) {
                throw new IllegalStateException("No artifacts in workflow session: " + requestId);
            }
            
            // Извлекаем необходимые данные
            String narrative = artifacts.get("narrative") != null ? String.valueOf(artifacts.get("narrative")) : "";
            String domainModel = artifacts.get("plantuml") != null ? String.valueOf(artifacts.get("plantuml")) : "";
            String useCaseModel = artifacts.get("useCaseModel") != null ? String.valueOf(artifacts.get("useCaseModel")) : "";
            String mvcModel = artifacts.get("mvcDiagram") != null ? String.valueOf(artifacts.get("mvcDiagram")) : "";
            
            // Проверяем наличие необходимых данных
            if (domainModel == null || domainModel.isBlank()) {
                throw new IllegalStateException("Domain model is required for Use Case decomposition");
            }
            if (useCaseModel == null || useCaseModel.isBlank()) {
                throw new IllegalStateException("Use Case model is required for Use Case decomposition");
            }
            if (mvcModel == null || mvcModel.isBlank()) {
                throw new IllegalStateException("MVC model is required for Use Case decomposition");
            }
            
            // Фильтруем модели, оставляя только релевантные части для конкретного Use Case
            // Это помогает уменьшить размер контекста и избежать превышения лимита токенов
            String filteredUseCaseModel = plantUmlFilter.filterUseCaseModel(
                    useCaseModel, useCaseAlias, useCaseName);
            String filteredMvcModel = plantUmlFilter.filterMvcModel(mvcModel, useCaseAlias);
            
            // Если модели все еще слишком большие, обрезаем их (fallback)
            // Максимальные размеры для каждой модели (примерно 3000 токенов на модель)
            int maxModelLength = 8000; // ~3000 токенов
            if (filteredUseCaseModel.length() > maxModelLength) {
                filteredUseCaseModel = plantUmlFilter.truncatePlantUml(filteredUseCaseModel, maxModelLength);
            }
            if (filteredMvcModel.length() > maxModelLength) {
                filteredMvcModel = plantUmlFilter.truncatePlantUml(filteredMvcModel, maxModelLength);
            }
            if (domainModel.length() > maxModelLength) {
                domainModel = plantUmlFilter.truncatePlantUml(domainModel, maxModelLength);
            }
            
            // Сокращаем narrative до разумного размера (2000 символов)
            String shortenedNarrative = plantUmlFilter.shortenNarrative(narrative, useCaseName, 2000);
            
            // Получаем RAG контекст (уменьшаем количество фрагментов для экономии токенов)
            String query = String.format("%s %s", useCaseName, useCaseAlias);
            var ragContext = ragService.retrieveContext(query, 2); // Уменьшили с 4 до 2
            log.info("RAG context retrieved: {} fragments", ragContext.fragmentsCount());
            
            // Генерируем сценарий для конкретного Use Case
            // Передаем дополнительный контекст о выбранном Use Case
            String enhancedNarrative = shortenedNarrative + "\n\nВыбранный Use Case для декомпозиции: " + useCaseName;
            
            log.info("Using filtered models - UseCase: {} chars (was {}), MVC: {} chars (was {}), Domain: {} chars, Narrative: {} chars (was {})",
                    filteredUseCaseModel.length(), useCaseModel.length(),
                    filteredMvcModel.length(), mvcModel.length(),
                    domainModel.length(),
                    shortenedNarrative.length(), narrative.length());
            
            String scenario = scenarioWriter.generateScenario(
                    enhancedNarrative,
                    domainModel,
                    filteredUseCaseModel,
                    filteredMvcModel,
                    ragContext.text()
            );
            
            log.info("Scenario generated for Use Case {}: {} chars", useCaseAlias, scenario.length());
            
            // Сохраняем сценарий в БД с привязкой к Use Case
            scenarioService.saveScenario(requestId, useCaseAlias, useCaseName, scenario);
            
            log.info("Scenario saved for Use Case: {} (alias: {}), requestId: {}", 
                    useCaseName, useCaseAlias, requestId);
            
            return scenario;
            
        } catch (Exception e) {
            log.error("Failed to decompose Use Case: {} (alias: {}), requestId: {}", 
                    useCaseName, useCaseAlias, requestId, e);
            throw new RuntimeException("Failed to decompose Use Case: " + e.getMessage(), e);
        }
    }
}

