# Рефакторинг - Наведение порядка в файлах

## Выполненные изменения

### 1. Удалены дубликаты файлов
- ✅ Удален `agentsServices/OrchestratorService.java` (старая версия без поддержки сессий)
- ✅ Удален `workflow/MVCModellerService.java` (дубликат, используется версия из `agentsServices/`)

### 2. Структура сервисов
Все сервисы агентов находятся в `agentsServices/`:
- `DomainModellerService.java`
- `EvaluatorService.java`
- `MVCModellerService.java`
- `NarrativeWriterService.java`
- `UseCaseModellerService.java`

### 3. Удален рудимент maxIterations
Закомментирован во всех местах:
- ✅ `OrchestratorService.java` - убраны все проверки и использование maxIter
- ✅ `WorkflowRequest.java` - закомментировано поле `constraints` и вложенный record
- ✅ `WorkflowSession.java` - закомментировано поле `maxIterations`
- ✅ `WorkflowSessionService.java` - закомментировано использование maxIterations

### 4. Исправлены импорты
Все workers используют правильные импорты из `agentsServices/`:
- ✅ `ModelWorker` → `DomainModellerService`
- ✅ `ReviewWorker` → `EvaluatorService`
- ✅ `MVCWorker` → `MVCModellerService`
- ✅ `UseCaseWorker` → `UseCaseModellerService`
- ✅ `NarrativeWorker` → `NarrativeWriterService`

### 5. Очистка кода
- ✅ Убрана неиспользуемая зависимость `sessionService` из `UserReviewWorker`

## Текущая структура

```
src/main/java/com/example/workflow/
├── agentsServices/          # Сервисы агентов
│   ├── DomainModellerService.java
│   ├── EvaluatorService.java
│   ├── MVCModellerService.java
│   ├── NarrativeWriterService.java
│   └── UseCaseModellerService.java
├── OrchestratorService.java  # Главный оркестратор (с поддержкой сессий)
├── WorkflowController.java
├── WorkflowRequest.java
├── WorkflowResponse.java
├── WorkflowSession*.java     # Сущности для сессий
├── *Worker.java              # Workers для агентов
└── ...
```

## Примечания

- Ошибки линтера связаны с тем, что IDE еще не подхватила зависимости JPA. После сборки проекта (`./gradlew build`) они должны исчезнуть.
- Поле `maxIterations` в БД оставлено для обратной совместимости, но больше не используется в коде.

