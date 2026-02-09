-- Добавляем колонку для мягкого удаления пользователей
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

-- Индекс для быстрой фильтрации активных пользователей
CREATE INDEX IF NOT EXISTS idx_users_deleted_at ON users(deleted_at) WHERE deleted_at IS NULL;

-- Комментарий к новой колонке
COMMENT ON COLUMN users.deleted_at IS 'Дата и время мягкого удаления пользователя. NULL означает, что пользователь активен.';
