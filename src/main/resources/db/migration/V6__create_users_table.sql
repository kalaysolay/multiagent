-- Создание таблицы для хранения пользователей
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Индекс для быстрого поиска по username
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);

-- Комментарии к таблице и колонкам
COMMENT ON TABLE users IS 'Хранилище пользователей системы';
COMMENT ON COLUMN users.id IS 'Уникальный идентификатор пользователя (UUID)';
COMMENT ON COLUMN users.username IS 'Уникальное имя пользователя для входа';
COMMENT ON COLUMN users.password IS 'Хешированный пароль (BCrypt)';
COMMENT ON COLUMN users.enabled IS 'Флаг активности пользователя';
COMMENT ON COLUMN users.created_at IS 'Дата и время создания пользователя';

-- Вставка дефолтного пользователя admin/admin
-- Пароль "admin" захеширован с помощью BCrypt (стоимость 10)
INSERT INTO users (username, password, enabled) 
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', true)
ON CONFLICT (username) DO NOTHING;
