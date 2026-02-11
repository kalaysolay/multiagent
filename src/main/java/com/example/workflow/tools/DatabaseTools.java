package com.example.workflow.tools;

import com.example.portal.agents.iconix.entity.UseCaseScenario;
import com.example.portal.agents.iconix.repository.UseCaseScenarioRepository;
import com.example.workflow.WorkflowSessionService;
import com.example.workflow.WorkflowSessionSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tools для запросов к базе данных.
 * Предоставляет LLM доступ к информации о workflow сессиях и сценариях.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseTools {
    
    private final WorkflowSessionService workflowSessionService;
    private final UseCaseScenarioRepository scenarioRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Получить список всех workflow сессий с их статусами.
     * 
     * @return JSON строка со списком сессий (requestId, status, narrative preview, createdAt)
     */
    public String getWorkflowSessions() {
        try {
            log.info("Tool call: getWorkflowSessions");
            List<WorkflowSessionSummary> sessions = workflowSessionService.getAllSessions();
            
            if (sessions.isEmpty()) {
                return "Нет сохраненных workflow сессий.";
            }
            
            List<Map<String, Object>> result = sessions.stream()
                    .limit(10) // Ограничиваем для краткости
                    .map(s -> {
                        java.util.Map<String, Object> map = new java.util.HashMap<>();
                        map.put("requestId", s.requestId());
                        map.put("status", s.status().toString());
                        map.put("author", s.author() != null ? s.author() : "N/A");
                        map.put("createdAt", s.createdAt() != null ? s.createdAt().toString() : "N/A");
                        return map;
                    })
                    .collect(Collectors.toList());
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error in getWorkflowSessions", e);
            return "Ошибка при получении списка сессий: " + e.getMessage();
        }
    }
    
    /**
     * Получить детальную информацию о конкретной workflow сессии.
     * 
     * @param sessionId ID сессии (requestId)
     * @return JSON строка с артефактами сессии (narrative, plantuml, useCaseModel, mvcDiagram, scenario)
     */
    public String getSessionDetails(String sessionId) {
        try {
            log.info("Tool call: getSessionDetails({})", sessionId);
            
            if (sessionId == null || sessionId.isBlank()) {
                return "Ошибка: необходимо указать sessionId";
            }
            
            var sessionData = workflowSessionService.getSessionData(sessionId);
            if (sessionData == null) {
                return "Сессия не найдена: " + sessionId;
            }
            
            Map<String, Object> result = Map.of(
                    "requestId", sessionData.requestId(),
                    "artifacts", sessionData.artifacts() != null ? sessionData.artifacts() : Map.of(),
                    "logsCount", sessionData.logs() != null ? sessionData.logs().size() : 0
            );
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            return "Сессия не найдена: " + sessionId;
        } catch (Exception e) {
            log.error("Error in getSessionDetails", e);
            return "Ошибка при получении данных сессии: " + e.getMessage();
        }
    }
    
    /**
     * Получить артефакт сессии по типу.
     * 
     * @param sessionId ID сессии
     * @param artifactType Тип артефакта: narrative, plantuml, useCaseModel, mvcDiagram, scenario
     * @return Содержимое артефакта или сообщение об ошибке
     */
    public String getSessionArtifact(String sessionId, String artifactType) {
        try {
            log.info("Tool call: getSessionArtifact({}, {})", sessionId, artifactType);
            
            if (sessionId == null || sessionId.isBlank()) {
                return "Ошибка: необходимо указать sessionId";
            }
            if (artifactType == null || artifactType.isBlank()) {
                return "Ошибка: необходимо указать artifactType (narrative, plantuml, useCaseModel, mvcDiagram, scenario)";
            }
            
            var sessionData = workflowSessionService.getSessionData(sessionId);
            if (sessionData == null || sessionData.artifacts() == null) {
                return "Сессия не найдена или артефакты отсутствуют: " + sessionId;
            }
            
            Object artifact = sessionData.artifacts().get(artifactType);
            if (artifact == null) {
                return "Артефакт '" + artifactType + "' не найден в сессии " + sessionId;
            }
            
            return artifact.toString();
        } catch (Exception e) {
            log.error("Error in getSessionArtifact", e);
            return "Ошибка при получении артефакта: " + e.getMessage();
        }
    }
    
    /**
     * Получить список Use Case сценариев для сессии.
     * 
     * @param sessionId ID сессии (requestId)
     * @return JSON строка со списком сценариев
     */
    public String getUseCaseScenarios(String sessionId) {
        try {
            log.info("Tool call: getUseCaseScenarios({})", sessionId);
            
            if (sessionId == null || sessionId.isBlank()) {
                return "Ошибка: необходимо указать sessionId";
            }
            
            List<UseCaseScenario> scenarios = scenarioRepository.findByRequestIdOrderByCreatedAtDesc(sessionId);
            
            if (scenarios.isEmpty()) {
                return "Сценарии для сессии " + sessionId + " не найдены.";
            }
            
            List<Map<String, Object>> result = scenarios.stream()
                    .map(s -> {
                        java.util.Map<String, Object> map = new java.util.HashMap<>();
                        map.put("id", s.getId().toString());
                        map.put("useCaseAlias", s.getUseCaseAlias() != null ? s.getUseCaseAlias() : "N/A");
                        map.put("useCaseName", s.getUseCaseName() != null ? s.getUseCaseName() : "N/A");
                        map.put("scenarioPreview", truncate(s.getScenarioContent(), 200));
                        map.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : "N/A");
                        return map;
                    })
                    .collect(Collectors.toList());
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error in getUseCaseScenarios", e);
            return "Ошибка при получении сценариев: " + e.getMessage();
        }
    }
    
    /**
     * Получить полный текст сценария по ID.
     * 
     * @param scenarioId UUID сценария
     * @return Полный текст сценария в AsciiDoc формате
     */
    public String getScenarioDetails(String scenarioId) {
        try {
            log.info("Tool call: getScenarioDetails({})", scenarioId);
            
            if (scenarioId == null || scenarioId.isBlank()) {
                return "Ошибка: необходимо указать scenarioId";
            }
            
            var scenario = scenarioRepository.findById(java.util.UUID.fromString(scenarioId));
            if (scenario.isEmpty()) {
                return "Сценарий не найден: " + scenarioId;
            }
            
            UseCaseScenario s = scenario.get();
            return String.format("""
                    Use Case: %s (%s)
                    Request ID: %s
                    Created: %s
                    
                    === Scenario ===
                    %s
                    """,
                    s.getUseCaseName(), s.getUseCaseAlias(),
                    s.getRequestId(),
                    s.getCreatedAt(),
                    s.getScenarioContent() != null ? s.getScenarioContent() : "N/A"
            );
        } catch (IllegalArgumentException e) {
            return "Некорректный формат scenarioId: " + scenarioId;
        } catch (Exception e) {
            log.error("Error in getScenarioDetails", e);
            return "Ошибка при получении сценария: " + e.getMessage();
        }
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
