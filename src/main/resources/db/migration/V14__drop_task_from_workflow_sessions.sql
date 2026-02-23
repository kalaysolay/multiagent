-- Удаление колонки task: ввод пользователя только через goal
ALTER TABLE workflow_sessions DROP COLUMN IF EXISTS task;
