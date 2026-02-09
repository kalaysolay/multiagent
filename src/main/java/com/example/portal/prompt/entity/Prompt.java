package com.example.portal.prompt.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Сущность "Промпт" — хранит системные промпты для ИИ-агентов.
 * Каждый промпт идентифицируется уникальным кодом (code),
 * по которому к нему обращается Java-код.
 */
@Entity
@Table(name = "prompts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Prompt {

    /** Уникальный идентификатор промпта (UUID, генерируется автоматически) */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    /** Уникальный строковый код промпта — используется в Java-сервисах для загрузки текста */
    @Column(name = "code", unique = true, nullable = false, length = 100)
    private String code;

    /** Человекочитаемое название промпта (отображается в UI) */
    @Column(name = "name", nullable = false)
    private String name;

    /** Полный текст промпта, отправляемый в LLM */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Описание назначения промпта (для администратора) */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Дата и время последнего обновления */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** UUID пользователя, который последним обновил промпт */
    @Column(name = "updated_by")
    private UUID updatedBy;

    /**
     * Автоматически устанавливает дату обновления при создании записи.
     * Вызывается JPA перед INSERT.
     */
    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    /**
     * Автоматически обновляет дату при каждом UPDATE.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
