-- ===================================================================
-- Миграция на Ollama nomic-embed-text (768 измерений).
-- ВНИМАНИЕ: Удаляет все существующие эмбеддинги (данные будут потеряны).
-- ===================================================================

-- Удаляем старую таблицу (была vector(1536) для OpenAI)
DROP TABLE IF EXISTS document_embeddings;

-- Создаём таблицу с vector(768) для Ollama nomic-embed-text
CREATE TABLE document_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    embedding vector(768),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Индекс для быстрого векторного поиска (HNSW, косинусное расстояние)
CREATE INDEX document_embeddings_embedding_idx
    ON document_embeddings
    USING hnsw (embedding vector_cosine_ops);

-- Индекс для сортировки по дате
CREATE INDEX idx_document_embeddings_created_at
    ON document_embeddings(created_at);

COMMENT ON TABLE document_embeddings IS 'Векторное хранилище для RAG (Ollama nomic-embed-text, 768 dim)';
COMMENT ON COLUMN document_embeddings.embedding IS 'Векторное представление текста (768 измерений для Ollama)';
