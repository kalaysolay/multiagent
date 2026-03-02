-- Добавляем поля для связи с Use Case
ALTER TABLE use_case_scenarios 
ADD COLUMN IF NOT EXISTS use_case_alias VARCHAR(255),
ADD COLUMN IF NOT EXISTS use_case_name VARCHAR(500);

-- Создаем индекс для быстрого поиска по алиасу Use Case
CREATE INDEX IF NOT EXISTS idx_use_case_scenarios_alias 
ON use_case_scenarios(request_id, use_case_alias);

-- Комментарии к новым полям
COMMENT ON COLUMN use_case_scenarios.use_case_alias IS 'Алиас Use Case (например, "reviewGiftByOfficer") для связи с декомпозированным сценарием';
COMMENT ON COLUMN use_case_scenarios.use_case_name IS 'Название Use Case (например, "Проверить подарок офицером")';

