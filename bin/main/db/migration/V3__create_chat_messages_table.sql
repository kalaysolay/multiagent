-- Создание таблицы для хранения сообщений чата
CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Индекс для быстрого поиска по дате создания (для получения последних сообщений)
CREATE INDEX IF NOT EXISTS idx_chat_messages_created_at ON chat_messages(created_at DESC);

-- Комментарии к таблице и колонкам
COMMENT ON TABLE chat_messages IS 'Хранилище сообщений чата с LLM';
COMMENT ON COLUMN chat_messages.id IS 'Уникальный идентификатор сообщения (UUID)';
COMMENT ON COLUMN chat_messages.role IS 'Роль отправителя: USER или ASSISTANT';
COMMENT ON COLUMN chat_messages.content IS 'Содержимое сообщения';
COMMENT ON COLUMN chat_messages.created_at IS 'Дата и время создания сообщения';

