package com.example.workflow.tools;

import com.example.portal.agents.iconix.entity.UseCaseScenario;
import com.example.portal.agents.iconix.model.WorkflowResponse;
import com.example.portal.agents.iconix.model.WorkflowStatus;
import com.example.portal.agents.iconix.repository.UseCaseScenarioRepository;
import com.example.workflow.WorkflowSessionService;
import com.example.workflow.WorkflowSessionSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для DatabaseTools.
 * 
 * Тестируемые методы:
 * 1. getWorkflowSessions - список workflow сессий
 * 2. getSessionDetails - детали конкретной сессии
 * 3. getSessionArtifact - получение конкретного артефакта
 * 4. getUseCaseScenarios - список use case сценариев
 * 5. getScenarioDetails - детали сценария
 */
@ExtendWith(MockitoExtension.class)
class DatabaseToolsTest {
    
    @Mock
    private WorkflowSessionService workflowSessionService;
    
    @Mock
    private UseCaseScenarioRepository scenarioRepository;
    
    @InjectMocks
    private DatabaseTools databaseTools;
    
    private static final String TEST_SESSION_ID = "test-session-123";
    private static final String TEST_NARRATIVE = "Тестовый нарратив для системы управления заказами";
    
    @BeforeEach
    void setUp() {
        // Initialization if needed
    }
    
    // ====================================================================
    // Тесты для getWorkflowSessions
    // ====================================================================
    
    @Test
    @DisplayName("getWorkflowSessions - возвращает форматированный список сессий")
    void getWorkflowSessions_returnsFormattedList() {
        // GIVEN
        List<WorkflowSessionSummary> sessions = List.of(
                new WorkflowSessionSummary(TEST_SESSION_ID, WorkflowStatus.COMPLETED, 
                        "System", Instant.now(), Instant.now()),
                new WorkflowSessionSummary("session-2", WorkflowStatus.RUNNING, 
                        "System", Instant.now(), Instant.now())
        );
        when(workflowSessionService.getAllSessions()).thenReturn(sessions);
        
        // WHEN
        String result = databaseTools.getWorkflowSessions();
        
        // THEN
        assertThat(result).contains(TEST_SESSION_ID);
        assertThat(result).contains("session-2");
        assertThat(result).contains("COMPLETED");
        assertThat(result).contains("RUNNING");
        verify(workflowSessionService, times(1)).getAllSessions();
    }
    
    @Test
    @DisplayName("getWorkflowSessions - возвращает сообщение при пустом списке")
    void getWorkflowSessions_returnsMessageWhenEmpty() {
        // GIVEN
        when(workflowSessionService.getAllSessions()).thenReturn(List.of());
        
        // WHEN
        String result = databaseTools.getWorkflowSessions();
        
        // THEN
        assertThat(result).contains("Нет сохраненных workflow сессий");
    }
    
    // ====================================================================
    // Тесты для getSessionDetails
    // ====================================================================
    
    @Test
    @DisplayName("getSessionDetails - возвращает артефакты существующей сессии")
    void getSessionDetails_existingSession_returnsArtifacts() {
        // GIVEN
        Map<String, Object> artifacts = Map.of(
                "narrative", TEST_NARRATIVE,
                "plantuml", "@startuml\nclass Order\n@enduml"
        );
        WorkflowResponse response = new WorkflowResponse(TEST_SESSION_ID, null, artifacts, List.of());
        when(workflowSessionService.getSessionData(TEST_SESSION_ID)).thenReturn(response);
        
        // WHEN
        String result = databaseTools.getSessionDetails(TEST_SESSION_ID);
        
        // THEN
        assertThat(result).contains(TEST_SESSION_ID);
        assertThat(result).contains("narrative");
        assertThat(result).contains("plantuml");
        verify(workflowSessionService, times(1)).getSessionData(TEST_SESSION_ID);
    }
    
    @Test
    @DisplayName("getSessionDetails - возвращает ошибку для несуществующей сессии")
    void getSessionDetails_nonExistingSession_returnsError() {
        // GIVEN
        when(workflowSessionService.getSessionData("non-existent"))
                .thenThrow(new IllegalArgumentException("Session not found"));
        
        // WHEN
        String result = databaseTools.getSessionDetails("non-existent");
        
        // THEN
        assertThat(result).contains("Сессия не найдена");
    }
    
    @Test
    @DisplayName("getSessionDetails - возвращает ошибку при пустом sessionId")
    void getSessionDetails_emptySessionId_returnsError() {
        // WHEN
        String result = databaseTools.getSessionDetails("");
        
        // THEN
        assertThat(result).contains("Ошибка");
        assertThat(result).contains("sessionId");
    }
    
    // ====================================================================
    // Тесты для getSessionArtifact
    // ====================================================================
    
    @Test
    @DisplayName("getSessionArtifact - возвращает конкретный артефакт")
    void getSessionArtifact_returnsSpecificArtifact() {
        // GIVEN
        String plantUmlCode = "@startuml\nclass Order\nclass Customer\n@enduml";
        Map<String, Object> artifacts = Map.of(
                "narrative", TEST_NARRATIVE,
                "plantuml", plantUmlCode
        );
        WorkflowResponse response = new WorkflowResponse(TEST_SESSION_ID, null, artifacts, List.of());
        when(workflowSessionService.getSessionData(TEST_SESSION_ID)).thenReturn(response);
        
        // WHEN
        String result = databaseTools.getSessionArtifact(TEST_SESSION_ID, "plantuml");
        
        // THEN
        assertThat(result).isEqualTo(plantUmlCode);
    }
    
    @Test
    @DisplayName("getSessionArtifact - возвращает ошибку для несуществующего артефакта")
    void getSessionArtifact_nonExistingArtifact_returnsError() {
        // GIVEN
        Map<String, Object> artifacts = Map.of("narrative", TEST_NARRATIVE);
        WorkflowResponse response = new WorkflowResponse(TEST_SESSION_ID, null, artifacts, List.of());
        when(workflowSessionService.getSessionData(TEST_SESSION_ID)).thenReturn(response);
        
        // WHEN
        String result = databaseTools.getSessionArtifact(TEST_SESSION_ID, "mvcDiagram");
        
        // THEN
        assertThat(result).contains("не найден");
    }
    
    // ====================================================================
    // Тесты для getUseCaseScenarios
    // ====================================================================
    
    @Test
    @DisplayName("getUseCaseScenarios - возвращает список сценариев")
    void getUseCaseScenarios_returnsScenariosList() {
        // GIVEN
        UseCaseScenario scenario1 = createTestScenario("UC1", "Создать заказ");
        UseCaseScenario scenario2 = createTestScenario("UC2", "Отменить заказ");
        
        when(scenarioRepository.findByRequestIdOrderByCreatedAtDesc(TEST_SESSION_ID))
                .thenReturn(List.of(scenario1, scenario2));
        
        // WHEN
        String result = databaseTools.getUseCaseScenarios(TEST_SESSION_ID);
        
        // THEN
        assertThat(result).contains("UC1");
        assertThat(result).contains("UC2");
        assertThat(result).contains("Создать заказ");
        assertThat(result).contains("Отменить заказ");
    }
    
    @Test
    @DisplayName("getUseCaseScenarios - возвращает сообщение при пустом списке")
    void getUseCaseScenarios_emptyList_returnsMessage() {
        // GIVEN
        when(scenarioRepository.findByRequestIdOrderByCreatedAtDesc(TEST_SESSION_ID))
                .thenReturn(List.of());
        
        // WHEN
        String result = databaseTools.getUseCaseScenarios(TEST_SESSION_ID);
        
        // THEN
        assertThat(result).contains("не найдены");
    }
    
    // ====================================================================
    // Тесты для getScenarioDetails
    // ====================================================================
    
    @Test
    @DisplayName("getScenarioDetails - возвращает полные данные сценария")
    void getScenarioDetails_returnsFullScenario() {
        // GIVEN
        UUID scenarioId = UUID.randomUUID();
        UseCaseScenario scenario = createTestScenario("UC1", "Создать заказ");
        scenario.setId(scenarioId);
        scenario.setScenarioContent("=== Сценарий создания заказа ===\n1. Пользователь...");
        
        when(scenarioRepository.findById(scenarioId)).thenReturn(Optional.of(scenario));
        
        // WHEN
        String result = databaseTools.getScenarioDetails(scenarioId.toString());
        
        // THEN
        assertThat(result).contains("UC1");
        assertThat(result).contains("Создать заказ");
        assertThat(result).contains("Сценарий создания заказа");
    }
    
    @Test
    @DisplayName("getScenarioDetails - возвращает ошибку для несуществующего сценария")
    void getScenarioDetails_nonExisting_returnsError() {
        // GIVEN
        UUID scenarioId = UUID.randomUUID();
        when(scenarioRepository.findById(scenarioId)).thenReturn(Optional.empty());
        
        // WHEN
        String result = databaseTools.getScenarioDetails(scenarioId.toString());
        
        // THEN
        assertThat(result).contains("не найден");
    }
    
    // ====================================================================
    // Вспомогательные методы
    // ====================================================================
    
    private UseCaseScenario createTestScenario(String alias, String name) {
        UseCaseScenario scenario = new UseCaseScenario();
        scenario.setId(UUID.randomUUID());
        scenario.setRequestId(TEST_SESSION_ID);
        scenario.setUseCaseAlias(alias);
        scenario.setUseCaseName(name);
        scenario.setCreatedAt(Instant.now());
        return scenario;
    }
}
