package com.example.portal.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для работы с локальным векторным хранилищем на базе PostgreSQL + pgvector.
 * Обеспечивает сохранение и поиск документов по векторному сходству.
 * Использует Spring AI EmbeddingModel (Ollama nomic-embed-text) для создания эмбеддингов.
 */
@Service
@Slf4j
public class LocalVectorStoreService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final String tableName;

    public LocalVectorStoreService(
            JdbcTemplate jdbcTemplate,
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel,
            @Value("${app.vector-store.table-name:document_embeddings}") String tableName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.tableName = tableName;
    }

    /**
     * Создаёт эмбеддинг для текста через Spring AI и конвертирует в строку для pgvector.
     */
    private List<Double> createEmbedding(String text) {
        float[] vector = embeddingModel.embed(text);
        List<Double> result = new ArrayList<>(vector.length);
        for (float f : vector) {
            result.add((double) f);
        }
        return result;
    }
    
    /**
     * Добавить документ в векторное хранилище.
     * Автоматически создает эмбеддинг для текста.
     * 
     * @param content текст документа
     * @param metadata дополнительные метаданные (может быть null)
     * @return UUID добавленного документа
     */
    @Transactional
    public UUID addDocument(String content, Map<String, Object> metadata) {
        try {
            // Создаём эмбеддинг через Spring AI (Ollama)
            List<Double> embedding = createEmbedding(content);
            
            // Конвертируем List<Double> в строку для pgvector формата "[1.0,2.0,3.0]"
            String embeddingString = "[" + embedding.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")) + "]";
            
            UUID id = UUID.randomUUID();
            String metadataJson = metadata != null && !metadata.isEmpty() 
                    ? new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata)
                    : null;
            
            String sql = String.format(
                    "INSERT INTO %s (id, content, embedding, metadata) VALUES (?, ?, ?::vector, ?::jsonb)",
                    tableName
            );
            
            jdbcTemplate.update(sql, id, content, embeddingString, metadataJson);
            
            log.debug("Added document to vector store: id={}, content_length={}", id, content.length());
            return id;
            
        } catch (Exception e) {
            log.error("Failed to add document to vector store", e);
            throw new RuntimeException("Failed to add document to vector store", e);
        }
    }
    
    /**
     * Добавить несколько документов пакетом.
     * 
     * @param documents список текстов документов
     * @return список UUID добавленных документов
     */
    @Transactional
    public List<UUID> addDocuments(List<String> documents) {
        List<UUID> ids = new ArrayList<>();
        for (String content : documents) {
            ids.add(addDocument(content, null));
        }
        return ids;
    }
    
    /**
     * Найти похожие документы по запросу.
     * 
     * @param query текст запроса
     * @param topK количество документов для возврата
     * @return список найденных документов с текстом и метаданными
     */
    public List<DocumentResult> findSimilar(String query, int topK) {
        try {
            // Создаём эмбеддинг для запроса через Spring AI
            List<Double> queryEmbedding = createEmbedding(query);
            String embeddingString = "[" + queryEmbedding.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")) + "]";
            
            // Выполняем поиск по косинусному расстоянию
            String sql = String.format(
                    "SELECT id, content, metadata, 1 - (embedding <=> ?::vector) as similarity " +
                    "FROM %s " +
                    "ORDER BY embedding <=> ?::vector " +
                    "LIMIT ?",
                    tableName
            );
            
            List<DocumentResult> results = jdbcTemplate.query(
                    sql,
                    new Object[]{embeddingString, embeddingString, topK},
                    (rs, rowNum) -> {
                        UUID id = UUID.fromString(rs.getString("id"));
                        String content = rs.getString("content");
                        double similarity = rs.getDouble("similarity");
                        
                        Map<String, Object> metadata = null;
                        String metadataJson = rs.getString("metadata");
                        if (metadataJson != null) {
                            try {
                                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                metadata = mapper.readValue(metadataJson, 
                                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                            } catch (Exception e) {
                                log.warn("Failed to parse metadata for document {}", id, e);
                            }
                        }
                        
                        return new DocumentResult(id, content, similarity, metadata);
                    }
            );
            
            log.debug("Found {} similar documents for query", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("Failed to find similar documents", e);
            throw new RuntimeException("Failed to find similar documents", e);
        }
    }
    
    /**
     * Получить список документов с пагинацией.
     *
     * @param offset смещение
     * @param limit  количество записей
     * @return список документов с превью контента и метаданными
     */
    public List<DocumentListItem> listDocuments(int offset, int limit) {
        try {
            String sql = String.format(
                    "SELECT id, LEFT(content, 200) as content_preview, metadata, created_at " +
                    "FROM %s " +
                    "ORDER BY created_at DESC " +
                    "LIMIT ? OFFSET ?",
                    tableName
            );
            return jdbcTemplate.query(
                    sql,
                    new Object[]{limit, offset},
                    (rs, rowNum) -> {
                        UUID id = UUID.fromString(rs.getString("id"));
                        String contentPreview = rs.getString("content_preview");
                        Instant createdAt = rs.getTimestamp("created_at") != null
                                ? rs.getTimestamp("created_at").toInstant()
                                : null;
                        Map<String, Object> metadata = null;
                        String metadataJson = rs.getString("metadata");
                        if (metadataJson != null) {
                            try {
                                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                metadata = mapper.readValue(metadataJson,
                                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                            } catch (Exception e) {
                                log.warn("Failed to parse metadata for document {}", id, e);
                            }
                        }
                        return new DocumentListItem(id, contentPreview, metadata, createdAt);
                    }
            );
        } catch (Exception e) {
            log.error("Failed to list documents", e);
            throw new RuntimeException("Failed to list documents", e);
        }
    }

    /**
     * Получить общее количество документов в хранилище.
     */
    public long countDocuments() {
        try {
            String sql = String.format("SELECT COUNT(*) FROM %s", tableName);
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Failed to count documents", e);
            throw new RuntimeException("Failed to count documents", e);
        }
    }

    /**
     * Удалить документ по ID.
     */
    @Transactional
    public void deleteDocument(UUID id) {
        String sql = String.format("DELETE FROM %s WHERE id = ?", tableName);
        jdbcTemplate.update(sql, id);
        log.debug("Deleted document from vector store: id={}", id);
    }
    
    /**
     * Результат поиска документа.
     */
    public record DocumentResult(UUID id, String content, double similarity, Map<String, Object> metadata) {}

    /**
     * Элемент списка документов (превью без полного контента).
     */
    public record DocumentListItem(UUID id, String contentPreview, Map<String, Object> metadata, Instant createdAt) {}
}

