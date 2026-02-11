package com.example.portal.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Сущность для хранения сообщений чата с LLM.
 * Поддерживает tool calling: хранение вызовов инструментов и их результатов.
 */
@Entity
@Table(name = "chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private MessageRole role;
    
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;
    
    /**
     * JSON массив tool calls для сообщений ASSISTANT.
     * Формат: [{"id": "call_xxx", "name": "tool_name", "arguments": {...}}]
     */
    @Column(name = "tool_calls_json", columnDefinition = "TEXT")
    private String toolCallsJson;
    
    /**
     * ID tool call для связи результата TOOL с вызовом в ASSISTANT.
     * Используется только для роли TOOL.
     */
    @Column(name = "tool_call_id", length = 100)
    private String toolCallId;
    
    /**
     * Имя вызванного инструмента.
     * Используется только для роли TOOL.
     */
    @Column(name = "tool_name", length = 100)
    private String toolName;
    
    /**
     * ID разговора для группировки сообщений в одну сессию чата.
     */
    @Column(name = "conversation_id", length = 36)
    private String conversationId;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    
    /**
     * Роли сообщений в чате.
     */
    public enum MessageRole {
        /** Сообщение от пользователя */
        USER,
        /** Ответ от LLM (может содержать tool calls) */
        ASSISTANT,
        /** Результат выполнения инструмента */
        TOOL
    }
}

