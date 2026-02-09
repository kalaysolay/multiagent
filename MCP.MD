# REST API и MCP (stdio) для публичного доступа к ICONIX Агентам

## Обзор

REST API предоставляет публичный доступ к ICONIX агентам. Каждый агент доступен как независимый endpoint, который можно вызывать отдельно, передавая необходимые данные.

Добавлен минимальный **MCP stdio сервер** (JSON-RPC 2.0), который запускается при флаге `--mcp-stdio` и работает через stdin/stdout.

**Компоненты:**
- REST API для простых интеграций через HTTP
- MCP stdio сервер (JSON-RPC 2.0) для совместимости с MCP-клиентами (Claude Desktop, Cursor)

**Ключевое отличие от OrchestratorService:**
- **OrchestratorService** использует жесткий план: `narrative → model → review → usecase → mvc → scenario`
- **REST/MCP** позволяют вызывать любой агент независимо, передавая необходимые входные данные

## Доступные Tools (Агенты)

### 1. `narrative` - Генерация нарратива
Генерирует нарратив на основе цели и задачи.

**Параметры:**
- `goal` (string, опционально) - Цель
- `task` (string, опционально) - Описание задачи
- `description` (string, опционально) - Описание для генерации

**Возвращает:** Сгенерированный нарратив в поле `narrativeOverride`

---

### 2. `model` - Генерация доменной модели
Генерирует или дорабатывает ICONIX доменную модель (PlantUML).

**Параметры:**
- `narrative` (string) - Нарратив предметной области
- `mode` (string, опционально) - Режим работы: `"generate"` (по умолчанию) или `"refine"`
- `domainModel` (string, опционально) - Существующая PlantUML модель (для refine)

**Возвращает:** PlantUML код доменной модели

---

### 3. `review` - Ревью модели или нарратива
Проводит ревью нарратива или модели, возвращает замечания.

**Параметры:**
- `target` (string, опционально) - Что ревьюировать: `"model"` (по умолчанию) или `"narrative"`
- `narrative` (string) - Нарратив (для target="narrative")
- `domainModel` (string, опционально) - PlantUML модель (для target="model")

**Возвращает:** Список замечаний (issues)

---

### 4. `usecase` - Генерация Use Case диаграммы
Генерирует диаграмму прецедентов (Use Case) на основе доменной модели.

**Параметры:**
- `narrative` (string, опционально) - Нарратив предметной области
- `domainModel` (string, **обязательно**) - PlantUML доменная модель

**Возвращает:** PlantUML код Use Case диаграммы

---

### 5. `mvc` - Генерация MVC диаграммы
Генерирует MVC диаграмму (Robustness diagram) на основе доменной модели и Use Case.

**Параметры:**
- `narrative` (string, опционально) - Нарратив предметной области
- `domainModel` (string, **обязательно**) - PlantUML доменная модель
- `useCaseModel` (string, **обязательно**) - PlantUML Use Case диаграмма

**Возвращает:** PlantUML код MVC диаграммы

---

### 6. `scenario` - Генерация Sequence диаграммы
Генерирует sequence диаграмму и сценарии Use Case в формате AsciiDoc.

**Параметры:**
- `narrative` (string, опционально) - Нарратив предметной области
- `domainModel` (string, **обязательно**) - PlantUML доменная модель
- `useCaseModel` (string, **обязательно**) - PlantUML Use Case диаграмма
- `mvcModel` (string, **обязательно**) - PlantUML MVC диаграмма

**Возвращает:** Sequence диаграмма (PlantUML) и сценарий в формате AsciiDoc

---

## Способы использования

### 1. HTTP REST API

#### Получить список доступных агентов

```bash
GET http://localhost:8080/api/agents
```

**Ответ:**
```json
{
  "tools": [
    {
      "name": "narrative",
      "description": "Генерирует нарратив на основе цели и задачи",
      "inputSchema": {
        "type": "object",
        "properties": {
          "goal": {"type": "string", "description": "Цель"},
          "task": {"type": "string", "description": "Описание задачи"}
        }
      }
    },
    ...
  ]
}
```

#### Вызвать агента

```bash
POST http://localhost:8080/api/agents/usecase
Content-Type: application/json

{
  "narrative": "Система управления заявками...",
  "domainModel": "@startuml\nentity \"Заявка\"\n...\n@enduml"
}
```

**Ответ:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "Use Case Model:\n```plantuml\n@startuml\nusecase \"Создать заявку\"\n...\n@enduml\n```"
    }
  ]
}
```

### 2. JSON-RPC 2.0 (опционально, для совместимости)

Если нужен JSON-RPC формат запросов:

```bash
POST http://localhost:8080/api/agents/jsonrpc
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list"
}
```

### 3. MCP stdio (настоящий MCP-режим)

Запуск приложения в MCP stdio-режиме:
```bash
java -jar app.jar --mcp-stdio
```

Протокол: JSON-RPC 2.0, одна строка JSON на stdin → одна строка JSON на stdout.

Поддерживаемые методы:
- `initialize`
- `tools/list`
- `tools/call`

Примеры:

**tools/list**
```json
{ "jsonrpc": "2.0", "id": 1, "method": "tools/list" }
```

**tools/call (usecase)**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "usecase",
    "arguments": {
      "narrative": "Система управления заявками...",
      "domainModel": "@startuml\nentity \"Заявка\"\n@enduml"
    }
  }
}
```

---

## Примеры использования

### Пример 1: Только UseCase диаграмма

У вас уже есть доменная модель, нужно сгенерировать только Use Case диаграмму.

```bash
curl -X POST http://localhost:8080/api/agents/usecase \
  -H "Content-Type: application/json" \
  -d '{
    "narrative": "Система управления заявками в интернет-магазине позволяет клиентам создавать заявки на возврат товаров",
    "domainModel": "@startuml\nentity \"Заявка\" as Request {\n  + id: UUID\n  + статус: String\n}\nentity \"Клиент\" as Customer\nCustomer ||--o{ Request : создает\n@enduml"
  }'
```

**Ответ:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "Use Case Model:\n```plantuml\n@startuml\nactor \"Клиент\" as Customer\nusecase \"Создать заявку на возврат\" as UC1\nCustomer --> UC1\n@enduml\n```"
    }
  ]
}
```

---

### Пример 2: Только Sequence диаграмма

У вас есть все необходимые диаграммы, нужно сгенерировать только sequence диаграмму.

```bash
curl -X POST http://localhost:8080/api/agents/scenario \
  -H "Content-Type: application/json" \
  -d '{
    "narrative": "Клиент создает заявку на возврат товара",
    "domainModel": "@startuml\nentity \"Заявка\"\n...\n@enduml",
    "useCaseModel": "@startuml\nusecase \"Создать заявку\"\n...\n@enduml",
    "mvcModel": "@startuml\nboundary \"Форма заявки\"\n...\n@enduml"
  }'
```

**Ответ:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "```plantuml\n@startuml sequence\nactor User\nboundary \"Форма заявки\" as Form\nUser -> Form: Заполнить данные\n...\n@enduml\n```"
    },
    {
      "type": "text",
      "text": "Scenario:\n=== UC: Создание заявки на возврат ===\n..."
    }
  ]
}
```

---

### Пример 3: Генерация доменной модели с нуля

Нужно сгенерировать доменную модель на основе описания.

```bash
curl -X POST http://localhost:8080/api/agents/model \
  -H "Content-Type: application/json" \
  -d '{
    "narrative": "Система управления складом. Склад содержит товары. Менеджер может создавать поставки, складской работник принимает товары на склад.",
    "mode": "generate"
  }'
```

**Ответ:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "```plantuml\n@startuml domain_model\nentity \"Склад\" as Warehouse\nentity \"Товар\" as Product\nentity \"Поставка\" as Shipment\nentity \"Менеджер\" as Manager\nentity \"Складской работник\" as Worker\n...\n@enduml\n```"
    }
  ]
}
```

---

### Пример 4: Полная цепочка (но вызовы независимые)

Вы можете вызывать агенты в любом порядке, передавая данные между вызовами:

**Шаг 1: Генерируем доменную модель**
```bash
# Вызов 1
RESPONSE1=$(curl -s -X POST http://localhost:8080/api/agents/model \
  -H "Content-Type: application/json" \
  -d '{
    "narrative": "Система управления заявками..."
  }')

# Извлекаем domainModel из ответа (в реальности нужно парсить JSON)
DOMAIN_MODEL="..."
```

**Шаг 2: Генерируем Use Case (используя результат Шага 1)**
```bash
curl -X POST http://localhost:8080/api/agents/usecase \
  -H "Content-Type: application/json" \
  -d "{
    \"narrative\": \"Система управления заявками...\",
    \"domainModel\": \"$DOMAIN_MODEL\"
  }"
```

---

## Обработка ошибок

Если переданы неполные данные или нарушены зависимости, агент вернет ошибку:

**Запрос:**
```bash
POST http://localhost:8080/api/agents/usecase
Content-Type: application/json

{
  "narrative": "Тест"
  // Не хватает domainModel
}
```

**Ответ:**
```json
{
  "isError": true,
  "content": [
    {
      "type": "text",
      "text": "Ошибка: No domain model (plantuml) in context; run model first."
    }
  ]
}
```

---

## Python клиент пример

```python
import requests
import json

class IconixAgentsClient:
    def __init__(self, base_url="http://localhost:8080/api/agents"):
        self.base_url = base_url
    
    def list_agents(self):
        """Получить список доступных агентов"""
        response = requests.get(self.base_url)
        return response.json()
    
    def call_agent(self, agent_name, arguments):
        """Вызвать агента"""
        response = requests.post(
            f"{self.base_url}/{agent_name}",
            json=arguments
        )
        return response.json()

# Использование
client = IconixAgentsClient()

# 1. Генерируем доменную модель
domain_result = client.call_agent("model", {
    "narrative": "Система управления заявками..."
})
domain_model = extract_plantuml(domain_result)  # функция для извлечения PlantUML

# 2. Генерируем Use Case
usecase_result = client.call_agent("usecase", {
    "narrative": "Система управления заявками...",
    "domainModel": domain_model
})

print(usecase_result)
```

---

## JavaScript/TypeScript клиент пример

```typescript
class IconixAgentsClient {
    constructor(private baseUrl: string = "http://localhost:8080/api/agents") {}
    
    async listAgents(): Promise<any> {
        const response = await fetch(this.baseUrl);
        return await response.json();
    }
    
    async callAgent(agentName: string, arguments: Record<string, any>): Promise<any> {
        const response = await fetch(`${this.baseUrl}/${agentName}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(arguments)
        });
        return await response.json();
    }
}

// Использование
const client = new IconixAgentsClient();

// Генерируем только UseCase диаграмму
const result = await client.callAgent("usecase", {
    narrative: "Система управления заявками...",
    domainModel: "@startuml\nentity \"Заявка\"\n@enduml"
});

console.log(result);
```

---

## Интеграция с Claude Desktop / Cursor (настоящий MCP)

⚠️ **Текущая реализация - это REST API, а не полноценный MCP протокол.**

Для интеграции с Claude Desktop или Cursor через настоящий MCP протокол потребуется:

1. **Реализация stdio transport** - чтение из stdin, запись в stdout
2. **JSON-RPC 2.0 обработка** - методы `initialize`, `tools/list`, `tools/call`
3. **Управление сессиями** - для поддержки контекста между вызовами

**Пример конфигурации для Claude Desktop (когда будет реализован настоящий MCP):**
```json
{
  "mcpServers": {
    "iconix-agents": {
      "command": "java",
      "args": ["-jar", "/path/to/iconix-agents.jar", "--mcp-stdio"],
      "env": {
        "OPENAI_API_KEY": "sk-..."
      }
    }
  }
}
```

**Текущий REST API** можно использовать через HTTP прокси или обертку для MCP клиентов.

---

## Отличия от OrchestratorService

| Аспект | OrchestratorService | REST API для агентов |
|--------|---------------------|---------------------|
| **План выполнения** | Жесткий: narrative → model → review → usecase → mvc → scenario | Гибкий: любой агент можно вызвать отдельно |
| **Входные данные** | Минимальные (goal, task, narrative) | Все необходимые данные передаются явно |
| **Зависимости** | Решаются автоматически через последовательный вызов | Передаются явно через аргументы |
| **Использование** | Через REST `/workflow/run` для полного workflow | Через REST `/api/agents/{agentName}` для точечного использования |
| **Применение** | Веб-портал с полным циклом ICONIX | Интеграция в другие системы, выборочное использование агентов |
| **Протокол** | REST API (кастомный) | REST API (стандартный HTTP) |

---

## Заключение

REST API для агентов предоставляет **универсальный и гибкий** способ использования ICONIX агентов:

✅ Вызывайте любой агент независимо  
✅ Передавайте необходимые данные явно  
✅ Не привязаны к жесткому плану оркестратора  
✅ Простой HTTP API для интеграции  

**Важно:** Это REST API, а не полноценный MCP протокол. Для настоящего MCP потребуется дополнительная реализация через stdio transport.

При этом существующий `/workflow/run` endpoint остается нетронутым для работы веб-портала с полным циклом ICONIX.

