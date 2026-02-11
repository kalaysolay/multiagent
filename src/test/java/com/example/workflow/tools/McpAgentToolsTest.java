package com.example.workflow.tools;

import com.example.mcp.IconixMCPServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для McpAgentTools.
 * 
 * Тестируемые методы:
 * 1. listAvailableTools - список доступных инструментов
 * 2. generateDomainModel - генерация доменной модели
 * 3. generateNarrative - генерация нарратива
 * 4. reviewModelOrNarrative - ревью
 * 5. generateUseCaseDiagram - генерация Use Case
 * 6. generateMvcDiagram - генерация MVC
 * 7. generateScenario - генерация сценария
 */
@ExtendWith(MockitoExtension.class)
class McpAgentToolsTest {
    
    @Mock
    private IconixMCPServer mcpServer;
    
    @InjectMocks
    private McpAgentTools mcpAgentTools;
    
    private static final String TEST_NARRATIVE = "Система управления заказами позволяет клиентам создавать и отслеживать заказы";
    private static final String TEST_PLANTUML = "@startuml\nclass Order {\n  +id: UUID\n  +status: String\n}\n@enduml";
    
    @BeforeEach
    void setUp() {
        // Initialization if needed
    }
    
    // ====================================================================
    // Тесты для listAvailableTools
    // ====================================================================
    
    @Test
    @DisplayName("listAvailableTools - возвращает форматированный список инструментов")
    void listAvailableTools_returnsFormattedList() {
        // GIVEN
        Map<String, Object> toolsResponse = Map.of(
                "tools", List.of(
                        Map.of("name", "model", "description", "Генерирует доменную модель"),
                        Map.of("name", "usecase", "description", "Генерирует Use Case диаграмму"),
                        Map.of("name", "userReview", "description", "Пауза для ревью")
                )
        );
        when(mcpServer.listTools()).thenReturn(toolsResponse);
        
        // WHEN
        String result = mcpAgentTools.listAvailableTools();
        
        // THEN
        assertThat(result).contains("model");
        assertThat(result).contains("usecase");
        assertThat(result).contains("Генерирует доменную модель");
        // userReview должен быть пропущен
        assertThat(result).doesNotContain("userReview");
    }
    
    // ====================================================================
    // Тесты для generateDomainModel
    // ====================================================================
    
    @Test
    @DisplayName("generateDomainModel - генерирует модель с режимом generate")
    void generateDomainModel_generatesPlantUml() {
        // GIVEN
        Map<String, Object> mcpResult = Map.of(
                "content", List.of(Map.of("type", "text", "text", "```plantuml\n" + TEST_PLANTUML + "\n```"))
        );
        when(mcpServer.callTool(eq("model"), any())).thenReturn(mcpResult);
        
        // WHEN
        String result = mcpAgentTools.generateDomainModel(TEST_NARRATIVE, "generate", null);
        
        // THEN
        assertThat(result).contains(TEST_PLANTUML);
        verify(mcpServer).callTool(eq("model"), argThat(args -> 
                TEST_NARRATIVE.equals(args.get("narrative")) && 
                "generate".equals(args.get("mode"))
        ));
    }
    
    @Test
    @DisplayName("generateDomainModel - возвращает ошибку при пустом нарративе")
    void generateDomainModel_emptyNarrative_returnsError() {
        // WHEN
        String result = mcpAgentTools.generateDomainModel("", "generate", null);
        
        // THEN
        assertThat(result).contains("Ошибка");
        assertThat(result).contains("narrative");
        verifyNoInteractions(mcpServer);
    }
    
    @Test
    @DisplayName("generateDomainModel - работает в режиме refine с существующей моделью")
    void generateDomainModel_refineMode_passesExistingModel() {
        // GIVEN
        String existingModel = "@startuml\nclass Order\n@enduml";
        Map<String, Object> mcpResult = Map.of(
                "content", List.of(Map.of("type", "text", "text", "```plantuml\n" + TEST_PLANTUML + "\n```"))
        );
        when(mcpServer.callTool(eq("model"), any())).thenReturn(mcpResult);
        
        // WHEN
        String result = mcpAgentTools.generateDomainModel(TEST_NARRATIVE, "refine", existingModel);
        
        // THEN
        verify(mcpServer).callTool(eq("model"), argThat(args -> 
                "refine".equals(args.get("mode")) && 
                existingModel.equals(args.get("domainModel"))
        ));
    }
    
    // ====================================================================
    // Тесты для generateNarrative
    // ====================================================================
    
    @Test
    @DisplayName("generateNarrative - генерирует нарратив по цели и задаче")
    void generateNarrative_generatesNarrative() {
        // GIVEN
        Map<String, Object> mcpResult = Map.of(
                "content", List.of(Map.of("type", "text", "text", "Generated Narrative:\n" + TEST_NARRATIVE))
        );
        when(mcpServer.callTool(eq("narrative"), any())).thenReturn(mcpResult);
        
        // WHEN
        String result = mcpAgentTools.generateNarrative("Создать систему заказов", "Описать процесс", null);
        
        // THEN
        assertThat(result).contains(TEST_NARRATIVE);
        verify(mcpServer).callTool(eq("narrative"), any());
    }
    
    // ====================================================================
    // Тесты для generateUseCaseDiagram
    // ====================================================================
    
    @Test
    @DisplayName("generateUseCaseDiagram - требует domainModel")
    void generateUseCaseDiagram_requiresDomainModel() {
        // WHEN
        String result = mcpAgentTools.generateUseCaseDiagram(TEST_NARRATIVE, null);
        
        // THEN
        assertThat(result).contains("Ошибка");
        assertThat(result).contains("domainModel");
        verifyNoInteractions(mcpServer);
    }
    
    @Test
    @DisplayName("generateUseCaseDiagram - генерирует диаграмму при наличии модели")
    void generateUseCaseDiagram_generatesWithModel() {
        // GIVEN
        String useCasePuml = "@startuml\nusecase \"Create Order\" as UC1\n@enduml";
        Map<String, Object> mcpResult = Map.of(
                "content", List.of(Map.of("type", "text", "text", "Use Case Model:\n```plantuml\n" + useCasePuml + "\n```"))
        );
        when(mcpServer.callTool(eq("usecase"), any())).thenReturn(mcpResult);
        
        // WHEN
        String result = mcpAgentTools.generateUseCaseDiagram(TEST_NARRATIVE, TEST_PLANTUML);
        
        // THEN
        assertThat(result).contains(useCasePuml);
        verify(mcpServer).callTool(eq("usecase"), argThat(args -> 
                TEST_PLANTUML.equals(args.get("domainModel"))
        ));
    }
    
    // ====================================================================
    // Тесты для generateMvcDiagram
    // ====================================================================
    
    @Test
    @DisplayName("generateMvcDiagram - требует domainModel и useCaseModel")
    void generateMvcDiagram_requiresBothModels() {
        // WHEN - без domainModel
        String result1 = mcpAgentTools.generateMvcDiagram(TEST_NARRATIVE, null, "usecase");
        assertThat(result1).contains("Ошибка");
        assertThat(result1).contains("domainModel");
        
        // WHEN - без useCaseModel
        String result2 = mcpAgentTools.generateMvcDiagram(TEST_NARRATIVE, TEST_PLANTUML, null);
        assertThat(result2).contains("Ошибка");
        assertThat(result2).contains("useCaseModel");
        
        verifyNoInteractions(mcpServer);
    }
    
    // ====================================================================
    // Тесты для generateScenario
    // ====================================================================
    
    @Test
    @DisplayName("generateScenario - требует все три модели")
    void generateScenario_requiresAllModels() {
        // WHEN - без одной из моделей
        String result = mcpAgentTools.generateScenario(TEST_NARRATIVE, TEST_PLANTUML, "usecase", null);
        
        // THEN
        assertThat(result).contains("Ошибка");
        assertThat(result).contains("mvcModel");
        verifyNoInteractions(mcpServer);
    }
    
    @Test
    @DisplayName("generateScenario - генерирует при наличии всех моделей")
    void generateScenario_generatesWithAllModels() {
        // GIVEN
        String scenarioResult = "Scenario:\n=== Create Order ===\n1. User enters data...";
        Map<String, Object> mcpResult = Map.of(
                "content", List.of(Map.of("type", "text", "text", scenarioResult))
        );
        when(mcpServer.callTool(eq("scenario"), any())).thenReturn(mcpResult);
        
        // WHEN
        String result = mcpAgentTools.generateScenario(
                TEST_NARRATIVE, TEST_PLANTUML, "useCaseModel", "mvcModel");
        
        // THEN
        assertThat(result).contains("Create Order");
        verify(mcpServer).callTool(eq("scenario"), argThat(args -> 
                args.containsKey("domainModel") && 
                args.containsKey("useCaseModel") && 
                args.containsKey("mvcModel")
        ));
    }
    
    // ====================================================================
    // Тесты для обработки ошибок MCP
    // ====================================================================
    
    @Test
    @DisplayName("MCP error - корректно обрабатывается ошибка от MCP сервера")
    void mcpError_isHandledCorrectly() {
        // GIVEN
        Map<String, Object> mcpResult = Map.of(
                "isError", true,
                "content", List.of(Map.of("type", "text", "text", "No domain model in context"))
        );
        when(mcpServer.callTool(eq("model"), any())).thenReturn(mcpResult);
        
        // WHEN
        String result = mcpAgentTools.generateDomainModel(TEST_NARRATIVE, "generate", null);
        
        // THEN
        assertThat(result).contains("Ошибка");
    }
}
