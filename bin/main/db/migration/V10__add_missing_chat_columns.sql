-- Добавление недостающих колонок в таблицу chat_messages
-- (V9 была применена ранее с другим содержимым)

-- Добавляем колонку для хранения JSON с tool calls (для сообщений ASSISTANT)
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS tool_calls_json TEXT;

-- Добавляем колонку для ID tool call (для сообщений TOOL - результатов вызова)
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS tool_call_id VARCHAR(100);

-- Добавляем колонку для имени инструмента (для сообщений TOOL)
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS tool_name VARCHAR(100);

-- Добавляем колонку для ID разговора (группировка сообщений в одну сессию чата)
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS conversation_id VARCHAR(36);

-- Индекс для быстрого поиска по conversation_id
CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation_id ON chat_messages(conversation_id);

-- Индекс для поиска по tool_call_id
CREATE INDEX IF NOT EXISTS idx_chat_messages_tool_call_id ON chat_messages(tool_call_id);
