package com.example.portal.prompt.service;

import com.example.portal.prompt.entity.Prompt;
import com.example.portal.prompt.entity.PromptHistory;
import com.example.portal.prompt.repository.PromptHistoryRepository;
import com.example.portal.prompt.repository.PromptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для работы со справочником промптов.
 * <p>
 * Основные функции:
 * - Получение текста промпта по уникальному коду (с кэшированием)
 * - Получение списка всех промптов (для админки)
 * - Обновление текста промпта с сохранением предыдущей версии в историю
 * <p>
 * Кэш реализован через ConcurrentHashMap для быстрого доступа без обращения к БД
 * при каждом вызове агента.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptService {

    private final PromptRepository promptRepository;
    private final PromptHistoryRepository promptHistoryRepository;

    /**
     * Кэш промптов: code -> content.
     * Используется ConcurrentHashMap для потокобезопасности.
     * При обновлении промпта кэш инвалидируется.
     */
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Получить текст промпта по его уникальному коду.
     * Сначала ищет в кэше, если не нашёл — загружает из БД и кладёт в кэш.
     *
     * @param code уникальный код промпта (например, "domain_modeller_system")
     * @return текст промпта
     * @throws IllegalArgumentException если промпт с таким кодом не найден
     */
    public String getByCode(String code) {
        // Сначала пробуем взять из кэша — это самый быстрый путь
        String cached = cache.get(code);
        if (cached != null) {
            return cached;
        }

        // Если в кэше нет — загружаем из БД
        Prompt prompt = promptRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Промпт с кодом '" + code + "' не найден в справочнике"));

        // Сохраняем в кэш для последующих обращений
        cache.put(code, prompt.getContent());
        log.debug("Промпт '{}' загружен из БД и помещён в кэш", code);

        return prompt.getContent();
    }

    /**
     * Получить промпт-сущность по коду (включая все метаданные).
     *
     * @param code уникальный код промпта
     * @return Optional с промптом
     */
    public Optional<Prompt> findByCode(String code) {
        return promptRepository.findByCode(code);
    }

    /**
     * Получить список всех промптов для отображения в админке.
     * Возвращает полные сущности со всеми метаданными.
     *
     * @return список всех промптов
     */
    public List<Prompt> getAll() {
        return promptRepository.findAll();
    }

    /**
     * Обновить текст промпта. При обновлении:
     * 1. Сохраняем старую версию текста в таблицу prompts_history
     * 2. Обновляем текст промпта в таблице prompts
     * 3. Инвалидируем кэш для этого кода
     *
     * @param code         уникальный код промпта
     * @param newContent   новый текст промпта
     * @param userId       UUID пользователя, который вносит изменение
     * @param changeReason причина изменения (опционально)
     * @return обновлённый промпт
     * @throws IllegalArgumentException если промпт с таким кодом не найден
     */
    @Transactional
    public Prompt updatePrompt(String code, String newContent, UUID userId, String changeReason) {
        // Ищем промпт по коду
        Prompt prompt = promptRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Промпт с кодом '" + code + "' не найден"));

        // Сохраняем старую версию в историю (до обновления)
        PromptHistory history = PromptHistory.builder()
                .prompt(prompt)
                .content(prompt.getContent())    // старый текст
                .changedAt(Instant.now())
                .changedBy(userId)
                .changeReason(changeReason)
                .build();
        promptHistoryRepository.save(history);
        log.info("Сохранена предыдущая версия промпта '{}' в историю (history id: {})",
                code, history.getId());

        // Обновляем текст промпта
        prompt.setContent(newContent);
        prompt.setUpdatedAt(Instant.now());
        prompt.setUpdatedBy(userId);
        Prompt saved = promptRepository.save(prompt);

        // Инвалидируем кэш для этого кода, чтобы при следующем обращении
        // агент получил обновлённый текст
        cache.remove(code);
        log.info("Промпт '{}' обновлён, кэш инвалидирован", code);

        return saved;
    }

    /**
     * Получить историю изменений конкретного промпта.
     *
     * @param code код промпта
     * @return список записей истории (от новых к старым)
     */
    public List<PromptHistory> getHistory(String code) {
        Prompt prompt = promptRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Промпт с кодом '" + code + "' не найден"));
        return promptHistoryRepository.findByPromptIdOrderByChangedAtDesc(prompt.getId());
    }

    /**
     * Очистить весь кэш промптов.
     * Используется в тестах и при необходимости принудительной перезагрузки.
     */
    public void clearCache() {
        cache.clear();
        log.info("Кэш промптов очищен");
    }
}
