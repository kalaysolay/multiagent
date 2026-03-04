package com.example.portal.chat.repository;

import com.example.portal.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    
    /**
     * Получить последние 3 сообщения, отсортированные по дате создания (от новых к старым).
     */
    List<ChatMessage> findTop3ByOrderByCreatedAtDesc();
    
    /**
     * Получить последние N сообщений, отсортированных по дате создания (от новых к старым).
     */
    @Query("SELECT m FROM ChatMessage m ORDER BY m.createdAt DESC LIMIT :limit")
    List<ChatMessage> findTopNByOrderByCreatedAtDesc(@Param("limit") int limit);
    
    /**
     * Получить сообщения по conversation ID, отсортированные хронологически.
     */
    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    /**
     * Получить последние N сообщений разговора (новые первые). Для контекста LLM берём с Pageable и разворачиваем.
     */
    List<ChatMessage> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);
    
    /**
     * Получить уникальные conversation IDs, отсортированные по последней активности.
     * GROUP BY + ORDER BY MAX(created_at) совместим с PostgreSQL (в отличие от SELECT DISTINCT + ORDER BY агрегата).
     */
    @Query(value = """
            SELECT cm.conversation_id FROM chat_messages cm
            WHERE cm.conversation_id IS NOT NULL
            GROUP BY cm.conversation_id
            ORDER BY MAX(cm.created_at) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findDistinctConversationIds(@Param("limit") int limit);
    
    /**
     * Получить сообщения по tool call ID.
     */
    List<ChatMessage> findByToolCallId(String toolCallId);
}

