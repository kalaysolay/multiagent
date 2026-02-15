package com.example.portal.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис векторизации документов.
 * <p>
 * Отвечает за подготовку текста к сохранению в векторное хранилище:
 * разбиение на чанки (chunks), добавление метаданных и запись через LocalVectorStoreService.
 * Используется админкой векторного хранилища для загрузки файлов и текста.
 * </p>
 */
@Service
@Slf4j
public class VectorizationService {

    /** Размер чанка в символах (рекомендуемый для эмбеддингов — до ~8k токенов, 2500 символов ≈ 600-700 токенов). */
    private static final int CHUNK_SIZE = 2500;

    /** Перекрытие между соседними чанками для сохранения контекста на границах. */
    private static final int CHUNK_OVERLAP = 200;

    private final LocalVectorStoreService vectorStoreService;

    public VectorizationService(LocalVectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * Добавить один документ в векторное хранилище.
     *
     * @param content  текст документа
     * @param metadata метаданные (source, chunk и т.д.; может быть null)
     * @return UUID добавленной записи
     */
    public UUID addDocument(String content, Map<String, Object> metadata) {
        return vectorStoreService.addDocument(content, metadata);
    }

    /**
     * Добавить несколько документов пакетом.
     *
     * @param documents список текстов
     * @return список UUID добавленных записей
     */
    public List<UUID> addDocuments(List<String> documents) {
        return vectorStoreService.addDocuments(documents);
    }

    /**
     * Загрузить содержимое файла с разбиением на чанки.
     * Каждый чанк сохраняется с метаданными source (имя файла) и chunk (индекс).
     *
     * @param filename имя файла (для metadata.source)
     * @param content  содержимое файла
     * @return список UUID добавленных чанков
     */
    public List<UUID> uploadFromFile(String filename, String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> chunks = chunkText(content, CHUNK_SIZE, CHUNK_OVERLAP);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = Map.of(
                    "source", filename,
                    "chunk", i
            );
            UUID id = vectorStoreService.addDocument(chunks.get(i), metadata);
            ids.add(id);
        }
        log.debug("Uploaded file {} as {} chunks", filename, chunks.size());
        return ids;
    }

    /**
     * Разбивает текст на чанки с перекрытием.
     * <p>
     * Например: chunkSize=10, overlap=2 даёт чанки [0..9], [8..17], [16..25]...
     * Перекрытие нужно, чтобы фразы на границах не теряли контекст при поиске.
     * </p>
     *
     * @param text      исходный текст
     * @param chunkSize размер чанка в символах
     * @param overlap   перекрытие между чанками
     * @return список чанков
     */
    public static List<String> chunkText(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int pos = 0;
        int step = chunkSize - overlap;
        while (pos < text.length()) {
            int end = Math.min(pos + chunkSize, text.length());
            chunks.add(text.substring(pos, end));
            if (end >= text.length()) {
                break;
            }
            pos += step;
        }
        return chunks;
    }
}
