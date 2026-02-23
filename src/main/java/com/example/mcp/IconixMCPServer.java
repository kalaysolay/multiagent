package com.example.mcp;

import com.example.portal.agents.iconix.service.WorkersRegistry;
import com.example.portal.agents.iconix.worker.Worker;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP сервер для ICONIX агентов
 * Предоставляет все Worker'ы как MCP tools для независимого вызова
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IconixMCPServer {
    
    private final WorkersRegistry workersRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Получить список всех доступных tools (агентов)
     */
    public Map<String, Object> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        for (Worker worker : workersRegistry.getAllWorkers()) {
            Map<String, Object> toolDef = createToolDefinition(worker);
            tools.add(toolDef);
        }
        
        return Map.of("tools", tools);
    }
    
    /**
     * Выполнить вызов tool (агента)
     */
    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
        try {
            log.info("MCP: Calling tool '{}' with arguments: {}", toolName, arguments.keySet());
            
            Worker worker = workersRegistry.get(toolName);
            
            // Создаем Worker.Context из аргументов
            Worker.Context ctx = createWorkerContext(arguments);
            
            // КЛЮЧЕВОЙ МОМЕНТ: маппим аргументы MCP в ctx.state
            // Это позволяет вызывать агенты независимо, передавая необходимые данные
            mapArgumentsToState(arguments, ctx.state);
            
            // Извлекаем специфичные для Worker аргументы
            Map<String, Object> workerArgs = extractWorkerArgs(worker, arguments);
            
            // Выполняем Worker
            worker.execute(ctx, workerArgs);
            
            // Формируем результат из Worker.Context.state
            return buildToolResult(ctx);
            
        } catch (Exception e) {
            log.error("Error executing MCP tool: " + toolName, e);
            return buildErrorResult(e);
        }
    }
    
    /**
     * Создает MCP tool definition для Worker
     */
    private Map<String, Object> createToolDefinition(Worker worker) {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", worker.name());
        tool.put("description", getWorkerDescription(worker));
        tool.put("inputSchema", createInputSchema(worker));
        return tool;
    }
    
    /**
     * Определяет описание Worker на основе его типа
     */
    private String getWorkerDescription(Worker worker) {
        return switch(worker.name()) {
            case "narrative" -> "Генерирует нарратив на основе цели";
            case "model" -> "Генерирует или дорабатывает ICONIX доменную модель (PlantUML)";
            case "review" -> "Проводит ревью нарратива или модели, возвращает замечания";
            case "usecase" -> "Генерирует диаграмму прецедентов (Use Case) на основе доменной модели";
            case "mvc" -> "Генерирует MVC диаграмму (Robustness diagram) на основе доменной модели и Use Case";
            case "scenario" -> "Генерирует sequence диаграмму и сценарии Use Case в формате AsciiDoc";
            case "userReview" -> "Пауза для пользовательского ревью (используется в оркестраторе)";
            default -> "ICONIX агент: " + worker.name();
        };
    }
    
    /**
     * Создает JSON Schema для входных параметров tool
     */
    private Map<String, Object> createInputSchema(Worker worker) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        
        // Базовые параметры для всех агентов
        properties.put("narrative", Map.of(
            "type", "string",
            "description", "Нарратив предметной области"
        ));
        properties.put("goal", Map.of(
            "type", "string",
            "description", "Цель"
        ));
        
        // Специфичные параметры для разных агентов
        switch(worker.name()) {
            case "model":
                properties.put("mode", Map.of(
                    "type", "string",
                    "enum", List.of("generate", "refine"),
                    "description", "Режим: generate - создать новую модель, refine - доработать существующую",
                    "default", "generate"
                ));
                properties.put("domainModel", Map.of(
                    "type", "string",
                    "description", "Существующая PlantUML модель (для mode=refine или если уже есть готовая модель)"
                ));
                break;
                
            case "review":
                properties.put("target", Map.of(
                    "type", "string",
                    "enum", List.of("model", "narrative"),
                    "description", "Что ревьюировать: model или narrative",
                    "default", "model"
                ));
                properties.put("domainModel", Map.of(
                    "type", "string",
                    "description", "PlantUML модель для ревью (если target=model)"
                ));
                break;
                
            case "usecase":
                properties.put("domainModel", Map.of(
                    "type", "string",
                    "description", "PlantUML доменная модель (обязательно)"
                ));
                required.add("domainModel");
                break;
                
            case "mvc":
                properties.put("domainModel", Map.of(
                    "type", "string",
                    "description", "PlantUML доменная модель (обязательно)"
                ));
                properties.put("useCaseModel", Map.of(
                    "type", "string",
                    "description", "PlantUML Use Case диаграмма (обязательно)"
                ));
                required.add("domainModel");
                required.add("useCaseModel");
                break;
                
            case "scenario":
                properties.put("domainModel", Map.of(
                    "type", "string",
                    "description", "PlantUML доменная модель (обязательно)"
                ));
                properties.put("useCaseModel", Map.of(
                    "type", "string",
                    "description", "PlantUML Use Case диаграмма (обязательно)"
                ));
                properties.put("mvcModel", Map.of(
                    "type", "string",
                    "description", "PlantUML MVC диаграмма (обязательно)"
                ));
                required.add("domainModel");
                required.add("useCaseModel");
                required.add("mvcModel");
                break;
        }
        
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        
        return schema;
    }
    
    /**
     * Создает Worker.Context из MCP аргументов
     */
    private Worker.Context createWorkerContext(Map<String, Object> arguments) {
        String requestId = UUID.randomUUID().toString();
        String narrative = (String) arguments.getOrDefault("narrative", "");
        String goal = (String) arguments.getOrDefault("goal", "");
        return new Worker.Context(requestId, narrative, goal);
    }
    
    /**
     * Маппинг аргументов MCP → ctx.state
     * Это позволяет передавать данные напрямую, без необходимости вызывать предыдущие агенты
     */
    private void mapArgumentsToState(Map<String, Object> arguments, Map<String, Object> state) {
        // Маппинг: domainModel → plantuml (для usecase, mvc, scenario)
        if (arguments.containsKey("domainModel")) {
            state.put("plantuml", arguments.get("domainModel"));
        }
        
        // Маппинг: useCaseModel → useCaseModel (для mvc, scenario)
        if (arguments.containsKey("useCaseModel")) {
            state.put("useCaseModel", arguments.get("useCaseModel"));
        }
        
        // Маппинг: mvcModel → mvcDiagram (для scenario)
        if (arguments.containsKey("mvcModel")) {
            state.put("mvcDiagram", arguments.get("mvcModel"));
        }
        
        // Также поддерживаем прямой ключ plantuml для обратной совместимости
        if (arguments.containsKey("plantuml")) {
            state.put("plantuml", arguments.get("plantuml"));
        }
    }
    
    /**
     * Извлекает специфичные аргументы для Worker
     */
    private Map<String, Object> extractWorkerArgs(Worker worker, Map<String, Object> arguments) {
        Map<String, Object> args = new HashMap<>();
        
        switch(worker.name()) {
            case "model":
                if (arguments.containsKey("mode")) {
                    args.put("mode", arguments.get("mode"));
                }
                break;
                
            case "review":
                if (arguments.containsKey("target")) {
                    args.put("target", arguments.get("target"));
                }
                break;
                
            case "narrative":
                if (arguments.containsKey("description")) {
                    args.put("description", arguments.get("description"));
                }
                break;
        }
        
        return args;
    }
    
    /**
     * Формирует результат MCP tool call из Worker.Context
     */
    private Map<String, Object> buildToolResult(Worker.Context ctx) {
        List<Map<String, Object>> content = new ArrayList<>();
        
        // Добавляем основные артефакты из ctx.state
        if (ctx.state.containsKey("plantuml")) {
            content.add(Map.of(
                "type", "text",
                "text", "```plantuml\n" + ctx.state.get("plantuml") + "\n```"
            ));
        }
        
        if (ctx.state.containsKey("useCaseModel")) {
            content.add(Map.of(
                "type", "text",
                "text", "Use Case Model:\n```plantuml\n" + ctx.state.get("useCaseModel") + "\n```"
            ));
        }
        
        if (ctx.state.containsKey("mvcDiagram")) {
            content.add(Map.of(
                "type", "text",
                "text", "MVC Diagram:\n```plantuml\n" + ctx.state.get("mvcDiagram") + "\n```"
            ));
        }
        
        if (ctx.state.containsKey("scenario")) {
            content.add(Map.of(
                "type", "text",
                "text", "Scenario:\n" + ctx.state.get("scenario")
            ));
        }
        
        // Issues для review
        if (ctx.state.containsKey("issues")) {
            content.add(Map.of(
                "type", "text",
                "text", "Issues:\n" + formatIssues(ctx.state.get("issues"))
            ));
        }
        
        if (ctx.state.containsKey("narrativeIssues")) {
            content.add(Map.of(
                "type", "text",
                "text", "Narrative Issues:\n" + formatIssues(ctx.state.get("narrativeIssues"))
            ));
        }
        
        // Narrative override
        if (ctx.state.containsKey("narrativeOverride")) {
            content.add(Map.of(
                "type", "text",
                "text", "Generated Narrative:\n" + ctx.state.get("narrativeOverride")
            ));
        }
        
        // Если нет контента, добавляем логи
        if (content.isEmpty() && !ctx.logs.isEmpty()) {
            content.add(Map.of(
                "type", "text",
                "text", "Logs:\n" + String.join("\n", ctx.logs)
            ));
        }
        
        return Map.of("content", content);
    }
    
    /**
     * Формирует результат с ошибкой
     */
    private Map<String, Object> buildErrorResult(Exception e) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of(
            "type", "text",
            "text", "Ошибка: " + e.getMessage()
        ));
        
        return Map.of(
            "isError", true,
            "content", content
        );
    }
    
    /**
     * Форматирует issues для вывода
     */
    private String formatIssues(Object issues) {
        if (issues == null) {
            return "Нет замечаний";
        }
        
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(issues);
        } catch (Exception e) {
            return issues.toString();
        }
    }
}

