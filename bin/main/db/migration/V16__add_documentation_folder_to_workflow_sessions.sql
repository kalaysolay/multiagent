-- Добавляем имя папки сгенерированной документации к сессии workflow.
-- Если поле заполнено — для этой сессии есть сгенерированная документация, показываем кнопку «Документация».
ALTER TABLE workflow_sessions
    ADD COLUMN IF NOT EXISTS documentation_folder_name VARCHAR(512) NULL;

COMMENT ON COLUMN workflow_sessions.documentation_folder_name IS 'Имя папки с сгенерированной ICONIX-документацией (относительно базового пути)';
