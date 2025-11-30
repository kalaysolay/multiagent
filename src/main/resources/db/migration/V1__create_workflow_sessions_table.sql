-- Создание таблицы для хранения сессий workflow
CREATE TABLE IF NOT EXISTS workflow_sessions (
    request_id VARCHAR(36) PRIMARY KEY,
    narrative TEXT,
    goal TEXT,
    task TEXT,
    context_state TEXT,
    logs TEXT,
    plan_json TEXT,
    current_step_index INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    user_review_data TEXT,
    max_iterations INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Индекс для быстрого поиска по статусу
CREATE INDEX IF NOT EXISTS idx_workflow_sessions_status ON workflow_sessions(status);

-- Индекс для поиска по дате создания
CREATE INDEX IF NOT EXISTS idx_workflow_sessions_created_at ON workflow_sessions(created_at);

-- Комментарии к таблице и колонкам
COMMENT ON TABLE workflow_sessions IS 'Хранилище сессий workflow для поддержки паузы и возобновления';
COMMENT ON COLUMN workflow_sessions.request_id IS 'Уникальный идентификатор сессии (UUID)';
COMMENT ON COLUMN workflow_sessions.narrative IS 'Нарратив пользователя';
COMMENT ON COLUMN workflow_sessions.goal IS 'Цель workflow';
COMMENT ON COLUMN workflow_sessions.task IS 'Задача workflow';
COMMENT ON COLUMN workflow_sessions.context_state IS 'JSON сериализация состояния контекста (state)';
COMMENT ON COLUMN workflow_sessions.logs IS 'JSON сериализация логов выполнения';
COMMENT ON COLUMN workflow_sessions.plan_json IS 'JSON сериализация плана выполнения (OrchestratorPlan)';
COMMENT ON COLUMN workflow_sessions.current_step_index IS 'Текущий индекс шага в плане (0-based)';
COMMENT ON COLUMN workflow_sessions.status IS 'Статус workflow: RUNNING, PAUSED_FOR_REVIEW, COMPLETED, FAILED';
COMMENT ON COLUMN workflow_sessions.user_review_data IS 'JSON с данными для пользовательского ревью';
COMMENT ON COLUMN workflow_sessions.max_iterations IS 'Максимальное количество итераций';
COMMENT ON COLUMN workflow_sessions.created_at IS 'Дата и время создания сессии';
COMMENT ON COLUMN workflow_sessions.updated_at IS 'Дата и время последнего обновления сессии';

