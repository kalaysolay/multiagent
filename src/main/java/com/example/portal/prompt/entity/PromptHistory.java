package com.example.portal.prompt.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Сущность "История промптов" — хранит предыдущие версии текстов промптов.
 * При каждом обновлении промпта старая версия сохраняется сюда,
 * что позволяет просматривать историю изменений и откатываться к прошлым версиям.
 */
@Entity
@Table(name = "prompts_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptHistory {

    /** Уникальный идентификатор записи истории */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    /** Ссылка на промпт, к которому относится эта запись истории */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_id", nullable = false)
    private Prompt prompt;

    /** Предыдущий текст промпта (до изменения) */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Дата и время, когда было произведено изменение */
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    /** UUID пользователя, который изменил промпт */
    @Column(name = "changed_by")
    private UUID changedBy;

    /** Причина изменения (опционально, указывается администратором) */
    @Column(name = "change_reason", length = 500)
    private String changeReason;

    /**
     * Автоматически устанавливает дату изменения при создании записи.
     */
    @PrePersist
    protected void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }
}
