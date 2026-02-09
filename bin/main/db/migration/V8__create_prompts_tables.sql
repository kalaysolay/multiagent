-- ======================================================================
-- Таблица prompts: справочник системных промптов
-- Все промпты агентов хранятся здесь, а не в коде
-- ======================================================================
CREATE TABLE IF NOT EXISTS prompts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(100)  UNIQUE NOT NULL,   -- уникальный код промпта (используется в Java-коде)
    name        VARCHAR(255)  NOT NULL,           -- человекочитаемое название
    content     TEXT          NOT NULL,           -- текст промпта
    description TEXT,                             -- описание назначения промпта
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  UUID          REFERENCES users(id)
);

-- Индекс для быстрого поиска по коду
CREATE INDEX IF NOT EXISTS idx_prompts_code ON prompts(code);

-- Комментарии
COMMENT ON TABLE  prompts             IS 'Справочник системных промптов для ИИ-агентов';
COMMENT ON COLUMN prompts.id          IS 'UUID промпта';
COMMENT ON COLUMN prompts.code        IS 'Уникальный строковый код для обращения из Java';
COMMENT ON COLUMN prompts.name        IS 'Человекочитаемое название промпта';
COMMENT ON COLUMN prompts.content     IS 'Полный текст промпта';
COMMENT ON COLUMN prompts.description IS 'Описание назначения промпта';
COMMENT ON COLUMN prompts.updated_at  IS 'Дата и время последнего обновления';
COMMENT ON COLUMN prompts.updated_by  IS 'UUID пользователя, обновившего промпт';

-- ======================================================================
-- Таблица prompts_history: история изменений промптов
-- При каждом обновлении промпта старая версия сохраняется здесь
-- ======================================================================
CREATE TABLE IF NOT EXISTS prompts_history (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_id     UUID          NOT NULL REFERENCES prompts(id) ON DELETE CASCADE,
    content       TEXT          NOT NULL,           -- предыдущий текст промпта
    changed_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by    UUID          REFERENCES users(id),
    change_reason VARCHAR(500)                      -- причина изменения (опционально)
);

-- Индекс для быстрой выборки истории по prompt_id
CREATE INDEX IF NOT EXISTS idx_prompts_history_prompt_id ON prompts_history(prompt_id);

-- Комментарии
COMMENT ON TABLE  prompts_history               IS 'История изменений промптов';
COMMENT ON COLUMN prompts_history.id            IS 'UUID записи истории';
COMMENT ON COLUMN prompts_history.prompt_id     IS 'Ссылка на промпт';
COMMENT ON COLUMN prompts_history.content       IS 'Предыдущий текст промпта до изменения';
COMMENT ON COLUMN prompts_history.changed_at    IS 'Дата и время изменения';
COMMENT ON COLUMN prompts_history.changed_by    IS 'UUID пользователя, изменившего промпт';
COMMENT ON COLUMN prompts_history.change_reason IS 'Причина изменения';
