package com.example.portal.chat.repository;

import com.example.portal.chat.entity.ChatMessage;
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
     * Получить уникальные conversation IDs, отсортированные по последней активности.
     */
    @Query("""
            SELECT DISTINCT m.conversationId FROM ChatMessage m 
            WHERE m.conversationId IS NOT NULL 
            ORDER BY MAX(m.createdAt) DESC 
            LIMIT :limit
            """)
    List<String> findDistinctConversationIds(@Param("limit") int limit);
    
    /**
     * Получить сообщения по tool call ID.
     */
    List<ChatMessage> findByToolCallId(String toolCallId);
}

