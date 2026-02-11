package com.example.workflow.tools;

import com.example.mcp.IconixMCPServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tools для вызова ICONIX MCP агентов.
 * Делегирует вызовы в IconixMCPServer и форматирует результаты для LLM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpAgentTools {
    
    private final IconixMCPServer mcpServer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Получить список всех доступных ICONIX инструментов с их описаниями.
     * 
     * @return Форматированный список инструментов
     */
    public String listAvailableTools() {
        try {
            log.info("Tool call: listAvailableTools");
            Map<String, Object> tools = mcpServer.listTools();
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolList = (List<Map<String, Object>>) tools.get("tools");
            
            StringBuilder sb = new StringBuilder("Доступные ICONIX инструменты:\n\n");
            for (Map<String, Object> tool : toolList) {
                String name = (String) tool.get("name");
                String description = (String) tool.get("description");
                
                // Пропускаем userReview - он для внутреннего использования
                if ("userReview".equals(name)) continue;
                
                sb.append("• **").append(name).append("**: ").append(description).append("\n");
            }
            
            sb.append("\nДля вызова инструмента используйте соответствующий метод (generateDomainModel, generateNarrative и т.д.)");
            
            return sb.toString();
        } catch (Exception e) {
            log.error("Error in listAvailableTools", e);
            return "Ошибка при получении списка инструментов: " + e.getMessage();
        }
    }
    
    /**
     * Генерация доменной модели (PlantUML) на основе нарратива.
     * 
     * @param narrative Нарратив предметной области
     * @param mode Режим: "generate" (по умолчанию) или "refine"
     * @param existingModel Существующая модель для режима refine (опционально)
     * @return PlantUML код доменной модели
     */
    public String generateDomainModel(String narrative, String mode, String existingModel) {
        try {
            log.info("Tool call: generateDomainModel(narrative={} chars, mode={})", 
                    narrative != null ? narrative.length() : 0, mode);
            
            if (narrative == null || narrative.isBlank()) {
                return "Ошибка: необходимо указать narrative (нарратив предметной области)";
            }
            
            Map<String, Object> args = new HashMap<>();
            args.put("narrative", narrative);
            args.put("mode", mode != null ? mode : "generate");
            if (existingModel != null && !existingModel.isBlank()) {
                args.put("domainModel", existingModel);
            }
            
            Map<String, Object> result = mcpServer.callTool("model", args);
            return formatMcpResult(result, "Domain Model");
        } catch (Exception e) {
            log.error("Error in generateDomainModel", e);
            return "Ошибка при генерации доменной модели: " + e.getMessage();
        }
    }
    
    /**
     * Генерация нарратива на основе цели и задачи.
     * 
     * @param goal Цель проекта
     * @param task Описание задачи
     * @param description Дополнительное описание
     * @return Сгенерированный нарратив
     */
    public String generateNarrative(String goal, String task, String description) {
        try {
            log.info("Tool call: generateNarrative(goal={} chars, task={} chars)", 
                    goal != null ? goal.length() : 0, task != null ? task.length() : 0);
            
            Map<String, Object> args = new HashMap<>();
            if (goal != null) args.put("goal", goal);
            if (task != null) args.put("task", task);
            if (description != null) args.put("description", description);
            
            Map<String, Object> result = mcpServer.callTool("narrative", args);
            return formatMcpResult(result, "Narrative");
        } catch (Exception e) {
            log.error("Error in generateNarrative", e);
            return "Ошибка при генерации нарратива: " + e.getMessage();
        }
    }
    
    /**
     * Ревью модели или нарратива.
     * 
     * @param target Что ревьюировать: "model" или "narrative"
     * @param narrative Нарратив для ревью
     * @param domainModel Доменная модель для ревью (если target=model)
     * @return Список замечаний
     */
    public String reviewModelOrNarrative(String target, String narrative, String domainModel) {
        try {
            log.info("Tool call: reviewModelOrNarrative(target={})", target);
            
            Map<String, Object> args = new HashMap<>();
            args.put("target", target != null ? target : "model");
            if (narrative != null) args.put("narrative", narrative);
            if (domainModel != null) args.put("domainModel", domainModel);
            
            Map<String, Object> result = mcpServer.callTool("review", args);
            return formatMcpResult(result, "Review");
        } catch (Exception e) {
            log.error("Error in reviewModelOrNarrative", e);
            return "Ошибка при ревью: " + e.getMessage();
        }
    }
    
    /**
     * Генерация Use Case диаграммы на основе доменной модели.
     * 
     * @param narrative Нарратив предметной области
     * @param domainModel PlantUML доменная модель (обязательно)
     * @return PlantUML код Use Case диаграммы
     */
    public String generateUseCaseDiagram(String narrative, String domainModel) {
        try {
            log.info("Tool call: generateUseCaseDiagram");
            
            if (domainModel == null || domainModel.isBlank()) {
                return "Ошибка: необходимо указать domainModel (PlantUML доменная модель)";
            }
            
            Map<String, Object> args = new HashMap<>();
            if (narrative != null) args.put("narrative", narrative);
            args.put("domainModel", domainModel);
            
            Map<String, Object> result = mcpServer.callTool("usecase", args);
            return formatMcpResult(result, "Use Case");
        } catch (Exception e) {
            log.error("Error in generateUseCaseDiagram", e);
            return "Ошибка при генерации Use Case диаграммы: " + e.getMessage();
        }
    }
    
    /**
     * Генерация MVC (Robustness) диаграммы.
     * 
     * @param narrative Нарратив предметной области
     * @param domainModel PlantUML доменная модель (обязательно)
     * @param useCaseModel PlantUML Use Case диаграмма (обязательно)
     * @return PlantUML код MVC диаграммы
     */
    public String generateMvcDiagram(String narrative, String domainModel, String useCaseModel) {
        try {
            log.info("Tool call: generateMvcDiagram");
            
            if (domainModel == null || domainModel.isBlank()) {
                return "Ошибка: необходимо указать domainModel";
            }
            if (useCaseModel == null || useCaseModel.isBlank()) {
                return "Ошибка: необходимо указать useCaseModel";
            }
            
            Map<String, Object> args = new HashMap<>();
            if (narrative != null) args.put("narrative", narrative);
            args.put("domainModel", domainModel);
            args.put("useCaseModel", useCaseModel);
            
            Map<String, Object> result = mcpServer.callTool("mvc", args);
            return formatMcpResult(result, "MVC");
        } catch (Exception e) {
            log.error("Error in generateMvcDiagram", e);
            return "Ошибка при генерации MVC диаграммы: " + e.getMessage();
        }
    }
    
    /**
     * Генерация Sequence диаграммы и сценария Use Case.
     * 
     * @param narrative Нарратив предметной области
     * @param domainModel PlantUML доменная модель (обязательно)
     * @param useCaseModel PlantUML Use Case диаграмма (обязательно)
     * @param mvcModel PlantUML MVC диаграмма (обязательно)
     * @return Sequence диаграмма и сценарий в AsciiDoc
     */
    public String generateScenario(String narrative, String domainModel, String useCaseModel, String mvcModel) {
        try {
            log.info("Tool call: generateScenario");
            
            if (domainModel == null || domainModel.isBlank()) {
                return "Ошибка: необходимо указать domainModel";
            }
            if (useCaseModel == null || useCaseModel.isBlank()) {
                return "Ошибка: необходимо указать useCaseModel";
            }
            if (mvcModel == null || mvcModel.isBlank()) {
                return "Ошибка: необходимо указать mvcModel";
            }
            
            Map<String, Object> args = new HashMap<>();
            if (narrative != null) args.put("narrative", narrative);
            args.put("domainModel", domainModel);
            args.put("useCaseModel", useCaseModel);
            args.put("mvcModel", mvcModel);
            
            Map<String, Object> result = mcpServer.callTool("scenario", args);
            return formatMcpResult(result, "Scenario");
        } catch (Exception e) {
            log.error("Error in generateScenario", e);
            return "Ошибка при генерации сценария: " + e.getMessage();
        }
    }
    
    /**
     * Форматирует результат MCP вызова для отображения.
     */
    @SuppressWarnings("unchecked")
    private String formatMcpResult(Map<String, Object> result, String toolName) {
        if (result.containsKey("isError") && Boolean.TRUE.equals(result.get("isError"))) {
            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            if (content != null && !content.isEmpty()) {
                return "Ошибка: " + content.get(0).get("text");
            }
            return "Ошибка при выполнении " + toolName;
        }
        
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        if (content == null || content.isEmpty()) {
            return toolName + " выполнен, но результат пустой.";
        }
        
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> item : content) {
            if ("text".equals(item.get("type"))) {
                sb.append(item.get("text")).append("\n");
            }
        }
        
        return sb.toString().trim();
    }
}
