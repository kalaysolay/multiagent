# Статус рефакторинга структуры проекта

## ✅ Что уже сделано

### 1. Структура пакетов создана
- ✅ `com.example.portal` - основной пакет портала
- ✅ `com.example.portal.config` - конфигурация
- ✅ `com.example.portal.shared` - общие компоненты
  - ✅ `shared.service` - общие сервисы (OpenAiRagService, OpenAiStorageService, PlantUmlRenderService)
  - ✅ `shared.utils` - утилиты (PromptUtils)
- ✅ `com.example.portal.agents.iconix` - агент Iconix
  - ✅ `agents.iconix.model` - модели данных
  - ✅ `agents.iconix.entity` - сущности JPA
  - ✅ `agents.iconix.repository` - репозитории
  - ✅ `agents.iconix.exception` - исключения
  - ✅ `agents.iconix.worker` - воркеры
  - ✅ `agents.iconix.service.agentservices` - сервисы агентов
  - ✅ `agents.iconix.service` - основные сервисы

### 2. Перенесенные файлы

#### Основные:
- ✅ `Application.java` → `com.example.portal.Application`
- ✅ `AiConfig.java` → `com.example.portal.config.AiConfig`

#### Общие компоненты:
- ✅ `PromptUtils.java` → `com.example.portal.shared.utils.PromptUtils`
- ✅ `OpenAiRagService.java` → `com.example.portal.shared.service.OpenAiRagService`
- ✅ `OpenAiStorageService.java` → `com.example.portal.shared.service.OpenAiStorageService`
- ✅ `PlantUmlRenderService.java` → `com.example.portal.shared.service.PlantUmlRenderService`

#### Модели Iconix:
- ✅ `Issue.java` → `com.example.portal.agents.iconix.model.Issue`
- ✅ `WorkflowRequest.java` → `com.example.portal.agents.iconix.model.WorkflowRequest`
- ✅ `WorkflowResponse.java` → `com.example.portal.agents.iconix.model.WorkflowResponse`
- ✅ `OrchestratorPlan.java` → `com.example.portal.agents.iconix.model.OrchestratorPlan`
- ✅ `PlanStep.java` → `com.example.portal.agents.iconix.model.PlanStep`
- ✅ `WorkflowStatus.java` → `com.example.portal.agents.iconix.model.WorkflowStatus`

#### Entity и Repository:
- ✅ `WorkflowSession.java` → `com.example.portal.agents.iconix.entity.WorkflowSession`
- ✅ `WorkflowSessionRepository.java` → `com.example.portal.agents.iconix.repository.WorkflowSessionRepository`

#### Exception:
- ✅ `PauseForUserReviewException.java` → `com.example.portal.agents.iconix.exception.PauseForUserReviewException`

#### Worker интерфейс и реализации:
- ✅ `Worker.java` → `com.example.portal.agents.iconix.worker.Worker`
- ✅ `NarrativeWorker.java` → `com.example.portal.agents.iconix.worker.NarrativeWorker`
- ✅ `ModelWorker.java` → `com.example.portal.agents.iconix.worker.ModelWorker`
- ✅ `ReviewWorker.java` → `com.example.portal.agents.iconix.worker.ReviewWorker`
- ✅ `UseCaseWorker.java` → `com.example.portal.agents.iconix.worker.UseCaseWorker`
- ✅ `MVCWorker.java` → `com.example.portal.agents.iconix.worker.MVCWorker`
- ✅ `UserReviewWorker.java` → `com.example.portal.agents.iconix.worker.UserReviewWorker`

#### Сервисы агентов:
- ✅ `NarrativeWriterService.java` → `com.example.portal.agents.iconix.service.agentservices.NarrativeWriterService`
- ✅ `DomainModellerService.java` → `com.example.portal.agents.iconix.service.agentservices.DomainModellerService`
- ✅ `EvaluatorService.java` → `com.example.portal.agents.iconix.service.agentservices.EvaluatorService`
- ✅ `UseCaseModellerService.java` → `com.example.portal.agents.iconix.service.agentservices.UseCaseModellerService`
- ✅ `MVCModellerService.java` → `com.example.portal.agents.iconix.service.agentservices.MVCModellerService`

#### Основные сервисы:
- ✅ `WorkersRegistry.java` → `com.example.portal.agents.iconix.service.WorkersRegistry`

## ⚠️ Что еще нужно сделать

### 1. Основные сервисы (требуют создания с обновленными импортами):
- ⏳ `WorkflowSessionService.java` → `com.example.portal.agents.iconix.service.WorkflowSessionService`
- ⏳ `OrchestratorService.java` → `com.example.portal.agents.iconix.service.OrchestratorService`

### 2. Контроллеры:
- ⏳ `WorkflowController.java` → `com.example.portal.agents.iconix.controller.WorkflowController`
- ⏳ `PlantUmlRenderController.java` → `com.example.portal.shared.controller.PlantUmlRenderController` (или в agents.iconix.controller)

### 3. Обновление импортов:
- ⏳ Все файлы должны использовать новые пути импорта
- ⏳ Проверить все зависимости между файлами

### 4. Удаление старых файлов:
- ⏳ После проверки компиляции удалить старые файлы из `com.example.workflow`

### 5. Промпты:
- ✅ Промпты уже в правильном месте (`src/main/resources/prompts/`)
- ⏳ При необходимости можно создать подпапки для разных агентов

## 📋 План дальнейших действий

1. Создать `WorkflowSessionService` с правильными импортами
2. Создать `OrchestratorService` с правильными импортами
3. Создать контроллеры с правильными импортами
4. Проверить компиляцию проекта
5. Исправить все ошибки импортов
6. Удалить старые файлы из `com.example.workflow`
7. Обновить пути в статических файлах (HTML, JS), если они ссылаются на старые эндпоинты

## 📁 Итоговая структура пакетов

```
com.example.portal
├── Application.java
├── config
│   └── AiConfig.java
├── shared
│   ├── service
│   │   ├── OpenAiRagService.java
│   │   ├── OpenAiStorageService.java
│   │   └── PlantUmlRenderService.java
│   └── utils
│       └── PromptUtils.java
└── agents
    └── iconix
        ├── controller
        │   ├── WorkflowController.java (нужно создать)
        │   └── PlantUmlRenderController.java (нужно создать)
        ├── entity
        │   └── WorkflowSession.java
        ├── exception
        │   └── PauseForUserReviewException.java
        ├── model
        │   ├── Issue.java
        │   ├── OrchestratorPlan.java
        │   ├── PlanStep.java
        │   ├── WorkflowRequest.java
        │   ├── WorkflowResponse.java
        │   └── WorkflowStatus.java
        ├── repository
        │   └── WorkflowSessionRepository.java
        ├── service
        │   ├── OrchestratorService.java (нужно создать)
        │   ├── WorkflowSessionService.java (нужно создать)
        │   ├── WorkersRegistry.java
        │   └── agentservices
        │       ├── DomainModellerService.java
        │       ├── EvaluatorService.java
        │       ├── MVCModellerService.java
        │       ├── NarrativeWriterService.java
        │       └── UseCaseModellerService.java
        └── worker
            ├── MVCWorker.java
            ├── ModelWorker.java
            ├── NarrativeWorker.java
            ├── ReviewWorker.java
            ├── UseCaseWorker.java
            ├── UserReviewWorker.java
            └── Worker.java
```

