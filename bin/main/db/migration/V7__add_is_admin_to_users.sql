-- Добавляем флаг администратора в таблицу пользователей
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_admin BOOLEAN NOT NULL DEFAULT false;

-- Комментарий к новой колонке
COMMENT ON COLUMN users.is_admin IS 'Флаг: является ли пользователь администратором';

-- Устанавливаем дефолтного пользователя admin как администратора
UPDATE users SET is_admin = true WHERE username = 'admin';
