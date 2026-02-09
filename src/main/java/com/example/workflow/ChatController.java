package com.example.workflow;

import com.example.portal.chat.entity.ChatMessage;
import com.example.portal.chat.repository.ChatMessageRepository;
import com.example.portal.shared.service.LlmService;
import com.example.portal.shared.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Контроллер для чата с LLM с поддержкой контекста из:
 * 1. Векторного хранилища (RAG) - релевантные документы из базы знаний
 * 2. Workflow сессий - контекст из предыдущих запусков ICONIX агентов
 * 
 * Контекст автоматически добавляется в промпт для улучшения качества ответов LLM.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    
    private final LlmService llmService;
    private final ChatMessageRepository chatMessageRepository;
    private final RagService ragService;
    private final WorkflowSessionService workflowSessionService;
    
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        // Сохраняем сообщение пользователя
        ChatMessage userMessage = ChatMessage.builder()
                .role(ChatMessage.MessageRole.USER)
                .content(request.message())
                .build();
        chatMessageRepository.save(userMessage);
        
        // Формируем промпт с контекстом
        String enhancedPrompt = buildEnhancedPrompt(request);
        
        // Генерируем ответ
        String response = llmService.generate(enhancedPrompt);
        
        // Сохраняем ответ ассистента
        ChatMessage assistantMessage = ChatMessage.builder()
                .role(ChatMessage.MessageRole.ASSISTANT)
                .content(response)
                .build();
        chatMessageRepository.save(assistantMessage);
        
        return new ChatResponse(response);
    }
    
    /**
     * Формирует промпт с контекстом из RAG и workflow сессий
     */
    private String buildEnhancedPrompt(ChatRequest request) {
        StringBuilder promptBuilder = new StringBuilder();
        String userMessage = request.message();
        
        // 1. Контекст из векторного хранилища (RAG)
        var ragContext = ragService.retrieveContext(userMessage, 5);
        if (ragContext.fragmentsCount() > 0) {
            promptBuilder.append("=== Контекст из документации ===\n");
            promptBuilder.append(ragContext.text());
            promptBuilder.append("\n\n");
            log.info("Added RAG context: {} fragments", ragContext.fragmentsCount());
        }
        
        // 2. Контекст из workflow сессий
        String workflowContext = buildWorkflowContext(request.workflowSessionId());
        if (!workflowContext.isBlank()) {
            promptBuilder.append("=== Контекст из workflow сессий ===\n");
            promptBuilder.append(workflowContext);
            promptBuilder.append("\n\n");
        }
        
        // 3. История чата (если передана)
        if (request.history() != null && !request.history().isEmpty()) {
            promptBuilder.append("=== История диалога ===\n");
            request.history().forEach(msg -> {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    promptBuilder.append("Пользователь: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    promptBuilder.append("Ассистент: ").append(content).append("\n");
                }
            });
            promptBuilder.append("\n");
        }
        
        // 4. Текущий вопрос пользователя
        promptBuilder.append("=== Вопрос пользователя ===\n");
        promptBuilder.append(userMessage);
        
        return promptBuilder.toString();
    }
    
    /**
     * Формирует контекст из workflow сессий
     */
    private String buildWorkflowContext(String workflowSessionId) {
        StringBuilder contextBuilder = new StringBuilder();
        
        try {
            // Если указана конкретная сессия, берем её
            if (workflowSessionId != null && !workflowSessionId.isBlank()) {
                var sessionData = workflowSessionService.getSessionData(workflowSessionId);
                if (sessionData != null) {
                    contextBuilder.append("Текущая workflow сессия:\n");
                    contextBuilder.append("- Request ID: ").append(workflowSessionId).append("\n");
                    if (sessionData.artifacts() != null) {
                        if (sessionData.artifacts().containsKey("narrative")) {
                            contextBuilder.append("- Нарратив: ").append(sessionData.artifacts().get("narrative")).append("\n");
                        }
                        if (sessionData.artifacts().containsKey("plantuml")) {
                            String plantuml = (String) sessionData.artifacts().get("plantuml");
                            if (plantuml != null && plantuml.length() > 0) {
                                contextBuilder.append("- Доменная модель (PlantUML): ").append(plantuml.substring(0, Math.min(500, plantuml.length()))).append("...\n");
                            }
                        }
                        if (sessionData.artifacts().containsKey("useCaseModel")) {
                            String useCase = (String) sessionData.artifacts().get("useCaseModel");
                            if (useCase != null && useCase.length() > 0) {
                                contextBuilder.append("- Use Case модель: ").append(useCase.substring(0, Math.min(500, useCase.length()))).append("...\n");
                            }
                        }
                    }
                    contextBuilder.append("\n");
                }
            } else {
                // Берем последние 3 сессии для общего контекста
                var recentSessions = workflowSessionService.getAllSessions();
                if (recentSessions != null && !recentSessions.isEmpty()) {
                    contextBuilder.append("Последние workflow сессии:\n");
                    recentSessions.stream()
                            .limit(3)
                            .forEach(sessionSummary -> {
                                try {
                                    // Получаем полные данные сессии для контекста
                                    var sessionData = workflowSessionService.getSessionData(sessionSummary.requestId());
                                    if (sessionData != null && sessionData.artifacts() != null) {
                                        contextBuilder.append("- Сессия: ").append(sessionSummary.requestId()).append("\n");
                                        contextBuilder.append("  Статус: ").append(sessionSummary.status()).append("\n");
                                        if (sessionData.artifacts().containsKey("narrative")) {
                                            String narrative = (String) sessionData.artifacts().get("narrative");
                                            if (narrative != null && !narrative.isBlank()) {
                                                contextBuilder.append("  Нарратив: ").append(narrative.substring(0, Math.min(200, narrative.length()))).append("...\n");
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.debug("Failed to get full session data for {}: {}", sessionSummary.requestId(), e.getMessage());
                                }
                            });
                    contextBuilder.append("\n");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to build workflow context: {}", e.getMessage());
        }
        
        return contextBuilder.toString();
    }
    
    @GetMapping("/history")
    public ChatHistoryResponse getHistory() {
        // Получаем последние 3 сообщения (от новых к старым)
        List<ChatMessage> messages = chatMessageRepository.findTop3ByOrderByCreatedAtDesc();
        
        // Разворачиваем список, чтобы получить хронологический порядок (от старых к новым)
        List<ChatMessage> reversed = messages.stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .collect(Collectors.toList());
        
        // Преобразуем в формат для фронтенда
        List<ChatMessageDto> history = reversed.stream()
                .map(msg -> new ChatMessageDto(
                        msg.getRole() == ChatMessage.MessageRole.USER ? "user" : "assistant",
                        msg.getContent(),
                        msg.getCreatedAt()
                ))
                .collect(Collectors.toList());
        
        return new ChatHistoryResponse(history);
    }
    
    public record ChatRequest(
            String message, 
            List<Map<String, String>> history,
            String workflowSessionId  // Опционально: ID конкретной workflow сессии для контекста
    ) {}
    public record ChatResponse(String response) {}
    public record ChatHistoryResponse(List<ChatMessageDto> messages) {}
    public record ChatMessageDto(String role, String content, java.time.Instant timestamp) {}
}

