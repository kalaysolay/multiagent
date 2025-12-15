-- Создание таблицы для хранения сценариев Use Case
CREATE TABLE IF NOT EXISTS use_case_scenarios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id VARCHAR(36) NOT NULL,
    scenario_content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Индекс для быстрого поиска по request_id
CREATE INDEX IF NOT EXISTS idx_use_case_scenarios_request_id ON use_case_scenarios(request_id);

-- Индекс для поиска по дате создания
CREATE INDEX IF NOT EXISTS idx_use_case_scenarios_created_at ON use_case_scenarios(created_at DESC);

-- Комментарии к таблице и колонкам
COMMENT ON TABLE use_case_scenarios IS 'Хранилище сценариев Use Case, сгенерированных ScenarioAgent';
COMMENT ON COLUMN use_case_scenarios.id IS 'Уникальный идентификатор сценария (UUID)';
COMMENT ON COLUMN use_case_scenarios.request_id IS 'Идентификатор сессии workflow, к которой относится сценарий';
COMMENT ON COLUMN use_case_scenarios.scenario_content IS 'Содержимое сценария в формате AsciiDoc';
COMMENT ON COLUMN use_case_scenarios.created_at IS 'Дата и время создания сценария';
COMMENT ON COLUMN use_case_scenarios.updated_at IS 'Дата и время последнего обновления сценария';

