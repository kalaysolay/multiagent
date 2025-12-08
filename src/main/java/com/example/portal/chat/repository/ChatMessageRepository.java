package com.example.portal.chat.repository;

import com.example.portal.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    
    /**
     * Получить последние 3 сообщения, отсортированные по дате создания (от новых к старым).
     */
    List<ChatMessage> findTop3ByOrderByCreatedAtDesc();
}

