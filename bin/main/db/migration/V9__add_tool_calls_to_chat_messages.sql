-- Добавление поддержки tool calls в таблицу chat_messages
-- Для хранения истории вызовов инструментов в чате с LLM

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

-- Индекс для поиска по tool_call_id (связь ASSISTANT tool_call с TOOL результатом)
CREATE INDEX IF NOT EXISTS idx_chat_messages_tool_call_id ON chat_messages(tool_call_id);

-- Комментарии
COMMENT ON COLUMN chat_messages.tool_calls_json IS 'JSON массив tool calls для сообщений ASSISTANT (id, name, arguments)';
COMMENT ON COLUMN chat_messages.tool_call_id IS 'ID tool call для связи результата TOOL с вызовом в ASSISTANT';
COMMENT ON COLUMN chat_messages.tool_name IS 'Имя вызванного инструмента для сообщений TOOL';
COMMENT ON COLUMN chat_messages.conversation_id IS 'ID разговора для группировки сообщений в одну сессию';
