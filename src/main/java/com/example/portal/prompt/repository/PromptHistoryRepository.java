package com.example.portal.prompt.repository;

import com.example.portal.prompt.entity.PromptHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Репозиторий для работы с таблицей prompts_history.
 * Позволяет получать историю изменений для конкретного промпта.
 */
@Repository
public interface PromptHistoryRepository extends JpaRepository<PromptHistory, UUID> {

    /**
     * Получить всю историю изменений для конкретного промпта,
     * отсортированную по дате (от новых к старым).
     *
     * @param promptId UUID промпта
     * @return список записей истории
     */
    List<PromptHistory> findByPromptIdOrderByChangedAtDesc(UUID promptId);
}
