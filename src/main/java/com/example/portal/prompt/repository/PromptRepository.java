package com.example.portal.prompt.repository;

import com.example.portal.prompt.entity.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с таблицей prompts.
 * Предоставляет базовые CRUD-операции и поиск по уникальному коду.
 */
@Repository
public interface PromptRepository extends JpaRepository<Prompt, UUID> {

    /**
     * Найти промпт по его уникальному коду.
     * Код используется в Java-сервисах для загрузки текста промпта.
     *
     * @param code уникальный код промпта (например, "domain_modeller_system")
     * @return Optional с промптом, если найден
     */
    Optional<Prompt> findByCode(String code);

    /**
     * Проверить, существует ли промпт с указанным кодом.
     *
     * @param code уникальный код промпта
     * @return true, если промпт с таким кодом уже есть в БД
     */
    boolean existsByCode(String code);
}
