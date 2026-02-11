package com.example.workflow;

import com.example.portal.chat.entity.ChatMessage;
import com.example.portal.chat.repository.ChatMessageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Контроллер для чата с LLM с поддержкой Tool Calling.
 * 
 * Возможности:
 * 1. Вызов ICONIX агентов (генерация доменных моделей, use case, MVC, сценариев)
 * 2. Запросы к БД (workflow сессии, артефакты, сценарии)
 * 3. Контекст из RAG и workflow сессий
 * 4. Сохранение истории с информацией о вызовах инструментов
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    
    private final ToolCallingChatService toolCallingChatService;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Основной endpoint чата с поддержкой tool calling.
     */
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        log.info("Chat request received. Message length: {}", 
                request.message() != null ? request.message().length() : 0);
        
        try {
            // Обрабатываем сообщение через ToolCallingChatService
            ToolCallingChatService.ChatResult result = toolCallingChatService.processMessage(
                    request.message(),
                    request.history(),
                    request.workflowSessionId(),
                    request.conversationId()
            );
            
            // Преобразуем tool calls в DTO
            List<ToolCallDto> toolCallDtos = result.toolCalls().stream()
                    .map(tc -> new ToolCallDto(tc.id(), tc.name(), tc.arguments(), tc.result()))
                    .collect(Collectors.toList());
            
            // Преобразуем диаграммы в DTO
            List<DiagramDto> diagramDtos = result.diagrams().stream()
                    .map(d -> new DiagramDto(d.title(), d.code(), d.type()))
                    .collect(Collectors.toList());
            
            return new ChatResponse(
                    result.response(),
                    toolCallDtos,
                    diagramDtos,
                    result.conversationId()
            );
            
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            return new ChatResponse(
                    "Произошла ошибка при обработке запроса: " + e.getMessage(),
                    List.of(),
                    List.of(),
                    request.conversationId()
            );
        }
    }
    
    /**
     * Получить историю чата с информацией о tool calls.
     * 
     * @param conversationId ID разговора (опционально)
     * @param limit Количество сообщений (по умолчанию 20)
     */
    @GetMapping("/history")
    public ChatHistoryResponse getHistory(
            @RequestParam(required = false) String conversationId,
            @RequestParam(defaultValue = "20") int limit) {
        
        List<ChatMessage> messages;
        
        if (conversationId != null && !conversationId.isBlank()) {
            // Получаем сообщения конкретного разговора
            messages = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        } else {
            // Получаем последние сообщения (от новых к старым, потом разворачиваем)
            messages = chatMessageRepository.findTopNByOrderByCreatedAtDesc(limit);
            // Разворачиваем для хронологического порядка
            messages = messages.stream()
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .collect(Collectors.toList());
        }
        
        // Преобразуем в формат для фронтенда
        List<ChatMessageDto> history = messages.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        
        return new ChatHistoryResponse(history);
    }
    
    /**
     * Получить список разговоров.
     */
    @GetMapping("/conversations")
    public ConversationsResponse getConversations(@RequestParam(defaultValue = "10") int limit) {
        List<String> conversationIds = chatMessageRepository.findDistinctConversationIds(limit);
        
        List<ConversationSummary> summaries = conversationIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(id -> {
                    List<ChatMessage> msgs = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(id);
                    if (msgs.isEmpty()) {
                        return null;
                    }
                    ChatMessage first = msgs.get(0);
                    ChatMessage last = msgs.get(msgs.size() - 1);
                    String preview = first.getContent();
                    if (preview.length() > 100) {
                        preview = preview.substring(0, 100) + "...";
                    }
                    return new ConversationSummary(id, preview, msgs.size(), first.getCreatedAt(), last.getCreatedAt());
                })
                .filter(s -> s != null)
                .collect(Collectors.toList());
        
        return new ConversationsResponse(summaries);
    }
    
    private ChatMessageDto convertToDto(ChatMessage msg) {
        String role = switch (msg.getRole()) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
        
        List<ToolCallDto> toolCalls = null;
        if (msg.getToolCallsJson() != null && !msg.getToolCallsJson().isBlank()) {
            try {
                List<Map<String, String>> parsed = objectMapper.readValue(
                        msg.getToolCallsJson(), 
                        new TypeReference<List<Map<String, String>>>() {}
                );
                toolCalls = parsed.stream()
                        .map(m -> new ToolCallDto(
                                m.get("id"),
                                m.get("name"),
                                m.get("arguments"),
                                null // результат хранится в отдельном TOOL сообщении
                        ))
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Failed to parse tool calls JSON", e);
            }
        }
        
        return new ChatMessageDto(
                role,
                msg.getContent(),
                msg.getCreatedAt(),
                toolCalls,
                msg.getToolCallId(),
                msg.getToolName(),
                msg.getConversationId()
        );
    }
    
    // --- Request/Response DTOs ---
    
    public record ChatRequest(
            String message, 
            List<Map<String, String>> history,
            String workflowSessionId,
            String conversationId
    ) {}
    
    public record ChatResponse(
            String response,
            List<ToolCallDto> toolCalls,
            List<DiagramDto> diagrams,
            String conversationId
    ) {}
    
    public record ChatHistoryResponse(List<ChatMessageDto> messages) {}
    
    public record ConversationsResponse(List<ConversationSummary> conversations) {}
    
    public record ChatMessageDto(
            String role,
            String content,
            Instant timestamp,
            List<ToolCallDto> toolCalls,
            String toolCallId,
            String toolName,
            String conversationId
    ) {}
    
    public record ToolCallDto(
            String id,
            String name,
            String arguments,
            String result
    ) {}
    
    public record DiagramDto(
            String title,
            String code,
            String type
    ) {}
    
    public record ConversationSummary(
            String conversationId,
            String preview,
            int messageCount,
            Instant startedAt,
            Instant lastMessageAt
    ) {}
}

