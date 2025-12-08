package com.example.workflow;

import com.example.portal.chat.entity.ChatMessage;
import com.example.portal.chat.repository.ChatMessageRepository;
import com.example.portal.shared.service.LlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    
    private final LlmService llmService;
    private final ChatMessageRepository chatMessageRepository;
    
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        // Сохраняем сообщение пользователя
        ChatMessage userMessage = ChatMessage.builder()
                .role(ChatMessage.MessageRole.USER)
                .content(request.message())
                .build();
        chatMessageRepository.save(userMessage);
        
        // Генерируем ответ
        String response = llmService.generate(request.message());
        
        // Сохраняем ответ ассистента
        ChatMessage assistantMessage = ChatMessage.builder()
                .role(ChatMessage.MessageRole.ASSISTANT)
                .content(response)
                .build();
        chatMessageRepository.save(assistantMessage);
        
        return new ChatResponse(response);
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
    
    public record ChatRequest(String message, List<Map<String, String>> history) {}
    public record ChatResponse(String response) {}
    public record ChatHistoryResponse(List<ChatMessageDto> messages) {}
    public record ChatMessageDto(String role, String content, java.time.Instant timestamp) {}
}

