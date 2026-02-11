package com.example.workflow;

import com.example.portal.chat.entity.ChatMessage;
import com.example.portal.chat.repository.ChatMessageRepository;
import com.example.portal.shared.service.RagService;
import com.example.workflow.tools.DatabaseTools;
import com.example.workflow.tools.McpAgentTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Сервис для чата с поддержкой Tool Calling.
 * Оркестрирует взаимодействие с LLM, включая вызовы инструментов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ToolCallingChatService {
    
    private final ChatClient chatClient;
    private final ChatMessageRepository chatMessageRepository;
    private final RagService ragService;
    private final WorkflowSessionService workflowSessionService;
    private final DatabaseTools databaseTools;
    private final McpAgentTools mcpAgentTools;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final int MAX_TOOL_ITERATIONS = 5;
    
    /**
     * Обрабатывает сообщение пользователя с поддержкой tool calling.
     * 
     * @param userMessage Сообщение пользователя
     * @param chatHistory История чата
     * @param workflowSessionId ID workflow сессии для контекста
     * @param conversationId ID разговора
     * @return Результат обработки с ответом и информацией о вызванных tools
     */
    public ChatResult processMessage(String userMessage, List<Map<String, String>> chatHistory, 
                                     String workflowSessionId, String conversationId) {
        log.info("Processing message with tool calling support. ConversationId: {}", conversationId);
        
        // Генерируем conversationId если не передан
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }
        
        // Сохраняем сообщение пользователя
        saveUserMessage(userMessage, conversationId);
        
        // Формируем системный промпт с контекстом
        String systemPrompt = buildSystemPrompt(userMessage, workflowSessionId);
        
        // Собираем историю сообщений для LLM
        List<Message> messages = buildMessages(systemPrompt, userMessage, chatHistory);
        
        // Создаем список функций для tool calling
        List<FunctionCallback> functions = createFunctionCallbacks();
        
        // Выполняем цикл tool calling
        List<ToolCallInfo> allToolCalls = new ArrayList<>();
        List<DiagramInfo> diagrams = new ArrayList<>();
        String finalResponse = null;
        
        int iteration = 0;
        while (iteration < MAX_TOOL_ITERATIONS) {
            iteration++;
            log.debug("Tool calling iteration {}", iteration);
            
            try {
                // Вызываем LLM
                ChatResponse response = callLlmWithTools(messages, functions);
                Generation generation = response.getResult();
                
                if (generation == null) {
                    log.warn("Empty generation from LLM");
                    finalResponse = "Извините, не удалось получить ответ.";
                    break;
                }
                
                AssistantMessage assistantMessage = generation.getOutput();
                String content = assistantMessage.getText();
                
                // Проверяем, есть ли tool calls
                if (!assistantMessage.hasToolCalls()) {
                    // Нет tool calls - финальный ответ
                    finalResponse = content;
                    saveAssistantMessage(content, null, conversationId);
                    break;
                }
                
                // Обрабатываем tool calls
                List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
                log.info("LLM requested {} tool calls", toolCalls.size());
                
                // Сохраняем assistant message с tool calls
                saveAssistantMessage(content, toolCalls, conversationId);
                
                // Добавляем assistant message в историю
                messages.add(assistantMessage);
                
                // Выполняем каждый tool call
                for (AssistantMessage.ToolCall toolCall : toolCalls) {
                    String toolName = toolCall.name();
                    String toolArgs = toolCall.arguments();
                    String toolCallId = toolCall.id();
                    
                    log.info("Executing tool: {} with id: {}", toolName, toolCallId);
                    
                    // Выполняем tool
                    String toolResult = executeFunction(toolName, toolArgs);
                    
                    // Сохраняем результат tool
                    saveToolMessage(toolCallId, toolName, toolResult, conversationId);
                    
                    // Добавляем в историю для LLM
                    ToolResponseMessage toolResponseMessage = new ToolResponseMessage(
                            List.of(new ToolResponseMessage.ToolResponse(toolCallId, toolName, toolResult)),
                            Map.of()
                    );
                    messages.add(toolResponseMessage);
                    
                    // Записываем информацию о вызове
                    allToolCalls.add(new ToolCallInfo(toolCallId, toolName, toolArgs, toolResult));
                    
                    // Извлекаем диаграммы из результата
                    extractDiagrams(toolName, toolResult, diagrams);
                }
                
            } catch (Exception e) {
                log.error("Error in tool calling iteration", e);
                finalResponse = "Произошла ошибка при обработке запроса: " + e.getMessage();
                break;
            }
        }
        
        if (finalResponse == null) {
            finalResponse = "Превышено максимальное количество итераций tool calling.";
        }
        
        return new ChatResult(finalResponse, allToolCalls, diagrams, conversationId);
    }
    
    /**
     * Формирует системный промпт с контекстом из RAG и workflow сессий.
     */
    private String buildSystemPrompt(String userMessage, String workflowSessionId) {
        StringBuilder promptBuilder = new StringBuilder();
        
        promptBuilder.append("""
                Ты - умный ассистент для работы с системой ICONIX моделирования.
                У тебя есть доступ к инструментам для:
                1. Получения информации о workflow сессиях и их артефактах (доменные модели, use case диаграммы, сценарии)
                2. Генерации различных диаграмм ICONIX (доменная модель, use case, MVC, sequence)
                3. Ревью моделей и нарративов
                
                Используй инструменты когда это необходимо для ответа на вопрос пользователя.
                Отвечай на русском языке.
                
                """);
        
        // Добавляем контекст из RAG
        var ragContext = ragService.retrieveContext(userMessage, 3);
        if (ragContext.fragmentsCount() > 0) {
            promptBuilder.append("=== Контекст из документации ===\n");
            promptBuilder.append(ragContext.text());
            promptBuilder.append("\n\n");
        }
        
        // Добавляем контекст из workflow сессии
        if (workflowSessionId != null && !workflowSessionId.isBlank()) {
            try {
                var sessionData = workflowSessionService.getSessionData(workflowSessionId);
                if (sessionData != null && sessionData.artifacts() != null) {
                    promptBuilder.append("=== Текущая workflow сессия: ").append(workflowSessionId).append(" ===\n");
                    if (sessionData.artifacts().containsKey("narrative")) {
                        String narrative = (String) sessionData.artifacts().get("narrative");
                        if (narrative != null && !narrative.isBlank()) {
                            promptBuilder.append("Нарратив: ").append(truncate(narrative, 500)).append("\n");
                        }
                    }
                    promptBuilder.append("\n");
                }
            } catch (Exception e) {
                log.debug("Could not load workflow session context: {}", e.getMessage());
            }
        }
        
        return promptBuilder.toString();
    }
    
    /**
     * Собирает историю сообщений для отправки в LLM.
     */
    private List<Message> buildMessages(String systemPrompt, String userMessage, List<Map<String, String>> chatHistory) {
        List<Message> messages = new ArrayList<>();
        
        // Системный промпт
        messages.add(new SystemMessage(systemPrompt));
        
        // История чата
        if (chatHistory != null) {
            for (Map<String, String> msg : chatHistory) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    messages.add(new UserMessage(content));
                } else if ("assistant".equals(role)) {
                    messages.add(new AssistantMessage(content));
                }
            }
        }
        
        // Текущее сообщение пользователя
        messages.add(new UserMessage(userMessage));
        
        return messages;
    }
    
    /**
     * Создает callbacks для всех доступных функций.
     */
    private List<FunctionCallback> createFunctionCallbacks() {
        List<FunctionCallback> callbacks = new ArrayList<>();
        
        // Database tools
        callbacks.add(FunctionCallback.builder()
                .function("getWorkflowSessions", (Void input) -> databaseTools.getWorkflowSessions())
                .description("Получить список workflow сессий с их статусами и датами создания")
                .inputType(Void.class)
                .build());
        
        callbacks.add(FunctionCallback.builder()
                .function("getSessionDetails", (SessionIdInput input) -> 
                        databaseTools.getSessionDetails(input.sessionId()))
                .description("Получить детали workflow сессии по ID (артефакты: narrative, plantuml, useCaseModel и др.)")
                .inputType(SessionIdInput.class)
                .build());
        
        callbacks.add(FunctionCallback.builder()
                .function("getSessionArtifact", (SessionArtifactInput input) -> 
                        databaseTools.getSessionArtifact(input.sessionId(), input.artifactType()))
                .description("Получить конкретный артефакт сессии: narrative, plantuml, useCaseModel, mvcDiagram, scenario")
                .inputType(SessionArtifactInput.class)
                .build());
        
        callbacks.add(FunctionCallback.builder()
                .function("getUseCaseScenarios", (SessionIdInput input) -> 
                        databaseTools.getUseCaseScenarios(input.sessionId()))
                .description("Получить список use case сценариев для workflow сессии")
                .inputType(SessionIdInput.class)
                .build());
        
        // MCP Agent tools
        callbacks.add(FunctionCallback.builder()
                .function("listAvailableTools", (Void input) -> mcpAgentTools.listAvailableTools())
                .description("Показать список всех доступных ICONIX инструментов с их описаниями")
                .inputType(Void.class)
                .build());
        
        callbacks.add(FunctionCallback.builder()
                .function("generateDomainModel", (DomainModelInput input) -> 
                        mcpAgentTools.generateDomainModel(
                                input.narrative(), 
                                input.mode() != null ? input.mode() : "generate", 
                                input.existingModel()))
                .description("Сгенерировать ICONIX доменную модель (PlantUML) на основе нарратива")
                .inputType(DomainModelInput.class)
                .build());
        
        callbacks.add(FunctionCallback.builder()
                .function("generateNarrative", (NarrativeInput input) -> 
                        mcpAgentTools.generateNarrative(input.goal(), input.task(), input.description()))
                .description("Сгенерировать нарратив предметной области на основе цели и задачи")
                .inputType(NarrativeInput.class)
                .build());
        
        callbacks.add(FunctionCallback.builder()
                .function("reviewModelOrNarrative", (ReviewInput input) -> 
                        mcpAgentTools.reviewModelOrNarrative(
                                input.target() != null ? input.target() : "model", 
                                input.narrative(), 
                                input.domainModel()))
                .description("Провести ревью модели или нарратива и получить замечания")
                .inputType(ReviewInput.class)
                .build());
        
        callbacks.add(FunctionCallback.builder()
                .function("generateUseCaseDiagram", (UseCaseInput input) -> 
                        mcpAgentTools.generateUseCaseDiagram(input.narrative(), input.domainModel()))
                .description("Сгенерировать Use Case диаграмму на основе доменной модели")
                .inputType(UseCaseInput.class)
                .build());
        
        callbacks.add(FunctionCallback.builder()
                .function("generateMvcDiagram", (MvcInput input) -> 
                        mcpAgentTools.generateMvcDiagram(input.narrative(), input.domainModel(), input.useCaseModel()))
                .description("Сгенерировать MVC (Robustness) диаграмму на основе доменной модели и Use Case")
                .inputType(MvcInput.class)
                .build());
        
        callbacks.add(FunctionCallback.builder()
                .function("generateScenario", (ScenarioInput input) -> 
                        mcpAgentTools.generateScenario(
                                input.narrative(), input.domainModel(), 
                                input.useCaseModel(), input.mvcModel()))
                .description("Сгенерировать Sequence диаграмму и сценарий Use Case")
                .inputType(ScenarioInput.class)
                .build());
        
        return callbacks;
    }
    
    /**
     * Вызывает LLM с поддержкой tools.
     */
    private ChatResponse callLlmWithTools(List<Message> messages, List<FunctionCallback> functions) {
        Prompt prompt = new Prompt(messages, 
                OpenAiChatOptions.builder()
                        .functionCallbacks(functions)
                        .build());
        
        return chatClient.prompt(prompt).call().chatResponse();
    }
    
    /**
     * Выполняет функцию по имени.
     */
    private String executeFunction(String functionName, String argumentsJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = argumentsJson != null && !argumentsJson.isBlank() 
                    ? objectMapper.readValue(argumentsJson, Map.class)
                    : new HashMap<>();
            
            return switch (functionName) {
                case "getWorkflowSessions" -> databaseTools.getWorkflowSessions();
                case "getSessionDetails" -> databaseTools.getSessionDetails((String) args.get("sessionId"));
                case "getSessionArtifact" -> databaseTools.getSessionArtifact(
                        (String) args.get("sessionId"), (String) args.get("artifactType"));
                case "getUseCaseScenarios" -> databaseTools.getUseCaseScenarios((String) args.get("sessionId"));
                case "listAvailableTools" -> mcpAgentTools.listAvailableTools();
                case "generateDomainModel" -> mcpAgentTools.generateDomainModel(
                        (String) args.get("narrative"),
                        (String) args.getOrDefault("mode", "generate"),
                        (String) args.get("existingModel"));
                case "generateNarrative" -> mcpAgentTools.generateNarrative(
                        (String) args.get("goal"),
                        (String) args.get("task"),
                        (String) args.get("description"));
                case "reviewModelOrNarrative" -> mcpAgentTools.reviewModelOrNarrative(
                        (String) args.getOrDefault("target", "model"),
                        (String) args.get("narrative"),
                        (String) args.get("domainModel"));
                case "generateUseCaseDiagram" -> mcpAgentTools.generateUseCaseDiagram(
                        (String) args.get("narrative"),
                        (String) args.get("domainModel"));
                case "generateMvcDiagram" -> mcpAgentTools.generateMvcDiagram(
                        (String) args.get("narrative"),
                        (String) args.get("domainModel"),
                        (String) args.get("useCaseModel"));
                case "generateScenario" -> mcpAgentTools.generateScenario(
                        (String) args.get("narrative"),
                        (String) args.get("domainModel"),
                        (String) args.get("useCaseModel"),
                        (String) args.get("mvcModel"));
                default -> "Неизвестная функция: " + functionName;
            };
        } catch (Exception e) {
            log.error("Error executing function: {}", functionName, e);
            return "Ошибка при выполнении функции " + functionName + ": " + e.getMessage();
        }
    }
    
    /**
     * Извлекает диаграммы из результата tool call.
     */
    private void extractDiagrams(String toolName, String toolResult, List<DiagramInfo> diagrams) {
        if (toolResult == null) return;
        
        // Ищем PlantUML блоки
        String[] plantUmlMarkers = {"```plantuml", "```puml"};
        for (String marker : plantUmlMarkers) {
            int startIdx = 0;
            while ((startIdx = toolResult.indexOf(marker, startIdx)) != -1) {
                int codeStart = toolResult.indexOf("\n", startIdx);
                int codeEnd = toolResult.indexOf("```", codeStart + 1);
                if (codeStart != -1 && codeEnd != -1) {
                    String plantUmlCode = toolResult.substring(codeStart + 1, codeEnd).trim();
                    String title = getDiagramTitle(toolName, diagrams.size());
                    diagrams.add(new DiagramInfo(title, plantUmlCode, "plantuml"));
                }
                startIdx = codeEnd != -1 ? codeEnd + 3 : toolResult.length();
            }
        }
    }
    
    private String getDiagramTitle(String toolName, int index) {
        return switch (toolName) {
            case "generateDomainModel" -> "Domain Model";
            case "generateUseCaseDiagram" -> "Use Case Diagram";
            case "generateMvcDiagram" -> "MVC Diagram";
            case "generateScenario" -> "Sequence Diagram";
            default -> "Diagram " + (index + 1);
        };
    }
    
    // --- Сохранение в БД ---
    
    private void saveUserMessage(String content, String conversationId) {
        ChatMessage message = ChatMessage.builder()
                .role(ChatMessage.MessageRole.USER)
                .content(content)
                .conversationId(conversationId)
                .build();
        chatMessageRepository.save(message);
    }
    
    private void saveAssistantMessage(String content, List<AssistantMessage.ToolCall> toolCalls, String conversationId) {
        String toolCallsJson = null;
        if (toolCalls != null && !toolCalls.isEmpty()) {
            try {
                List<Map<String, String>> serializable = toolCalls.stream()
                        .map(tc -> Map.of(
                                "id", tc.id(),
                                "name", tc.name(),
                                "arguments", tc.arguments() != null ? tc.arguments() : ""
                        ))
                        .toList();
                toolCallsJson = objectMapper.writeValueAsString(serializable);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize tool calls", e);
            }
        }
        
        ChatMessage message = ChatMessage.builder()
                .role(ChatMessage.MessageRole.ASSISTANT)
                .content(content != null ? content : "")
                .toolCallsJson(toolCallsJson)
                .conversationId(conversationId)
                .build();
        chatMessageRepository.save(message);
    }
    
    private void saveToolMessage(String toolCallId, String toolName, String result, String conversationId) {
        ChatMessage message = ChatMessage.builder()
                .role(ChatMessage.MessageRole.TOOL)
                .content(result)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .conversationId(conversationId)
                .build();
        chatMessageRepository.save(message);
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
    
    // --- Input types for function callbacks ---
    
    public record SessionIdInput(String sessionId) {}
    public record SessionArtifactInput(String sessionId, String artifactType) {}
    public record DomainModelInput(String narrative, String mode, String existingModel) {}
    public record NarrativeInput(String goal, String task, String description) {}
    public record ReviewInput(String target, String narrative, String domainModel) {}
    public record UseCaseInput(String narrative, String domainModel) {}
    public record MvcInput(String narrative, String domainModel, String useCaseModel) {}
    public record ScenarioInput(String narrative, String domainModel, String useCaseModel, String mvcModel) {}
    
    // --- Result types ---
    
    public record ChatResult(
            String response,
            List<ToolCallInfo> toolCalls,
            List<DiagramInfo> diagrams,
            String conversationId
    ) {}
    
    public record ToolCallInfo(
            String id,
            String name,
            String arguments,
            String result
    ) {}
    
    public record DiagramInfo(
            String title,
            String code,
            String type
    ) {}
}
