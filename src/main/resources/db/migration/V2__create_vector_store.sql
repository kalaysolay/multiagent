-- ===================================================================
-- ВАЖНО: Эта миграция требует установленного расширения pgvector
-- ===================================================================
-- Если вы получаете ошибку при выполнении этой миграции:
--   1. Установите pgvector в PostgreSQL (см. PGVECTOR_SETUP.md)
--   2. ИЛИ временно отключите эту миграцию (переименуйте в .sql.disabled)
--
-- Инструкции по установке: см. файл PGVECTOR_SETUP.md в корне проекта
-- ===================================================================

-- Включение расширения pgvector
-- Если расширение не установлено, миграция завершится с ошибкой
CREATE EXTENSION IF NOT EXISTS vector;

-- Создание таблицы для хранения векторных эмбеддингов документов
CREATE TABLE IF NOT EXISTS document_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    embedding vector(1536),  -- Размерность для OpenAI text-embedding-ada-002
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Индекс для быстрого векторного поиска (HNSW индекс)
CREATE INDEX IF NOT EXISTS document_embeddings_embedding_idx 
    ON document_embeddings 
    USING hnsw (embedding vector_cosine_ops);

-- Индекс для поиска по дате создания
CREATE INDEX IF NOT EXISTS idx_document_embeddings_created_at 
    ON document_embeddings(created_at);

-- Комментарии к таблице и колонкам
COMMENT ON TABLE document_embeddings IS 'Векторное хранилище для RAG: хранит эмбеддинги документов';
COMMENT ON COLUMN document_embeddings.id IS 'Уникальный идентификатор документа';
COMMENT ON COLUMN document_embeddings.content IS 'Оригинальный текст документа';
COMMENT ON COLUMN document_embeddings.embedding IS 'Векторное представление документа (1536 измерений для OpenAI)';
COMMENT ON COLUMN document_embeddings.metadata IS 'Дополнительные метаданные документа в формате JSON';
COMMENT ON COLUMN document_embeddings.created_at IS 'Дата и время создания записи';
COMMENT ON COLUMN document_embeddings.updated_at IS 'Дата и время последнего обновления записи';
