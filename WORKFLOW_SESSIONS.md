# Workflow Sessions - Сохранение контекста и возобновление

## Описание

Реализована поддержка сохранения состояния workflow в PostgreSQL с возможностью паузы для пользовательского ревью и последующего возобновления.

## Архитектура

### База данных

- **Таблица**: `workflow_sessions`
- **База данных**: `iconix_agent_db` (PostgreSQL)
- **Миграции**: Flyway (`src/main/resources/db/migration/`)

### Компоненты

1. **WorkflowSession** - JPA сущность для хранения состояния
2. **WorkflowSessionService** - сервис для сохранения/загрузки сессий
3. **UserReviewWorker** - worker для паузы workflow
4. **OrchestratorService** - обновлен для поддержки паузы/возобновления

## Workflow с User Review

Новый план выполнения:
```
Narrative → Model → Review → Model(refine) → UserReview → UseCase → MVC
```

### Статусы workflow

- `RUNNING` - выполняется
- `PAUSED_FOR_REVIEW` - приостановлен для пользовательского ревью
- `COMPLETED` - завершен
- `FAILED` - завершен с ошибкой

## API Endpoints

### 1. Запуск нового workflow

```bash
POST /workflow/run
Content-Type: application/json

{
  "narrative": "Описание задачи...",
  "goal": "Цель",
  "task": "Задача",
  "constraints": {
    "maxIterations": 6
  }
}
```

**Ответ:**
```json
{
  "requestId": "uuid",
  "orchestrator": {...},
  "artifacts": {
    "_status": "PAUSED_FOR_REVIEW",
    "_reviewData": {
      "narrative": "...",
      "domainModel": "...",
      "issues": [...]
    },
    "narrative": "...",
    "plantuml": "..."
  },
  "logs": [...]
}
```

### 2. Возобновление workflow после ревью

```bash
POST /workflow/resume
Content-Type: application/json

{
  "requestId": "uuid-из-предыдущего-ответа",
  "narrative": "Обновленный нарратив (опционально)",
  "domainModel": "Обновленная доменная модель в PlantUML (опционально)"
}
```

**Ответ:**
```json
{
  "requestId": "uuid",
  "orchestrator": {...},
  "artifacts": {
    "_status": "COMPLETED",
    "narrative": "...",
    "plantuml": "...",
    "useCaseModel": "...",
    "mvcDiagram": "..."
  },
  "logs": [...]
}
```

## Настройка базы данных

### Переменные окружения

```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=iconix_agent_db
DB_USER=postgres
DB_PASSWORD=postgres
```

### Создание базы данных

```sql
CREATE DATABASE iconix_agent_db;
```

Миграции Flyway выполнятся автоматически при старте приложения.

## Как это работает

1. **Запуск workflow**: Пользователь отправляет запрос на `/workflow/run`
2. **Выполнение шагов**: Orchestrator выполняет шаги плана
3. **Пауза на UserReview**: Когда достигается шаг `userReview`, workflow сохраняется в БД со статусом `PAUSED_FOR_REVIEW`
4. **Пользовательский ревью**: Пользователь получает ответ с данными для ревью (`_reviewData`)
5. **Возобновление**: Пользователь отправляет обновленные данные на `/workflow/resume`
6. **Продолжение**: Workflow загружается из БД и продолжается с места остановки

## Определение точки остановки

Текущая позиция в workflow определяется полем `currentStepIndex` в таблице `workflow_sessions`. Это индекс шага в плане (0-based), на котором произошла пауза.

## Пример использования

```javascript
// 1. Запуск workflow
const response1 = await fetch('/workflow/run', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    narrative: 'Описание системы...',
    goal: 'Построить модель',
    task: '',
    constraints: { maxIterations: 6 }
  })
});

const data1 = await response1.json();
console.log('Request ID:', data1.requestId);
console.log('Status:', data1.artifacts._status);

if (data1.artifacts._status === 'PAUSED_FOR_REVIEW') {
  // 2. Пользователь просматривает данные для ревью
  const reviewData = data1.artifacts._reviewData;
  console.log('Narrative:', reviewData.narrative);
  console.log('Domain Model:', reviewData.domainModel);
  console.log('Issues:', reviewData.issues);
  
  // 3. Пользователь вносит правки и возобновляет
  const response2 = await fetch('/workflow/resume', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      requestId: data1.requestId,
      narrative: 'Обновленный нарратив...',
      domainModel: 'Обновленная модель...'
    })
  });
  
  const data2 = await response2.json();
  console.log('Final Status:', data2.artifacts._status);
}
```

