-- ===================================================================
-- Миграция для DEV/CORPORATE: OpenAI-совместимые эмбеддинги (1536 измерений).
-- Используется при app.embedding-provider=OPENAI или CORPORATE.
-- ВНИМАНИЕ: Удаляет все существующие эмбеддинги (данные будут потеряны).
-- При переходе с OLLAMA (768) требуется ре-векторизация документов.
-- ===================================================================

-- Удаляем старую таблицу (была vector(768) для Ollama или vector(1536) для OpenAI)
DROP TABLE IF EXISTS document_embeddings;

-- Создаём таблицу с vector(1536) для OpenAI / корпоративного API
CREATE TABLE document_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    embedding vector(1536),
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

COMMENT ON TABLE document_embeddings IS 'Векторное хранилище для RAG (OpenAI/Corporate, 1536 dim)';
COMMENT ON COLUMN document_embeddings.embedding IS 'Векторное представление текста (1536 измерений для OpenAI/Corporate)';
