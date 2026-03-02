-- Таблица для хранения декомпозированных MVC-диаграмм по Use Case
CREATE TABLE IF NOT EXISTS use_case_mvc (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id VARCHAR(36) NOT NULL,
    use_case_alias VARCHAR(255),
    use_case_name VARCHAR(500),
    mvc_plantuml TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_use_case_mvc_request_id ON use_case_mvc(request_id);
CREATE INDEX IF NOT EXISTS idx_use_case_mvc_request_id_alias ON use_case_mvc(request_id, use_case_alias);
CREATE INDEX IF NOT EXISTS idx_use_case_mvc_created_at ON use_case_mvc(created_at DESC);

COMMENT ON TABLE use_case_mvc IS 'Декомпозированные MVC-диаграммы (PlantUML) по Use Case';
COMMENT ON COLUMN use_case_mvc.request_id IS 'Идентификатор сессии workflow';
COMMENT ON COLUMN use_case_mvc.use_case_alias IS 'Алиас Use Case';
COMMENT ON COLUMN use_case_mvc.use_case_name IS 'Название Use Case';
COMMENT ON COLUMN use_case_mvc.mvc_plantuml IS 'PlantUML код MVC-диаграммы';
