package com.example.portal.shared.service;

/**
 * Интерфейс для работы с RAG (Retrieval-Augmented Generation) - получение контекста из векторного хранилища.
 * Поддерживает разные реализации: OpenAI Vector Store и локальное хранилище (PostgreSQL + pgvector).
 */
public interface RagService {
    
    /**
     * Получить релевантный контекст из векторного хранилища для заданного запроса.
     * 
     * @param query текстовый запрос для поиска похожих документов
     * @param topK количество наиболее релевантных документов для возврата
     * @return результат поиска с текстом контекста и метаданными
     */
    ContextResult retrieveContext(String query, int topK);
    
    /**
     * Результат поиска в векторном хранилище.
     */
    record ContextResult(String text, int fragmentsCount, boolean vectorStoreAvailable) {
        public String text() {
            return text == null ? "" : text;
        }
    }
}

