# ICONIX Agents (Spring Boot + Spring AI)

Минимальный проект с эндпоинтом `/workflow/run`:
- Оркестратор запускает цепочку агентов: `NarrativeWorker` → `ModelWorker` → `ReviewWorker` → `ModelWorker(refine)`.
- Все агенты используют RAG-контекст из OpenAI Vector Store для терминологии и знания о системе Комплаенс.
- На выходе: итоговый PlantUML, замечания ревью, логи шагов.
- Дополнительно можно сложить артефакты в OpenAI Files + Vector Store.

## Быстрый старт

1) Установи Java 21 и Gradle 8+ (или используй gradle wrapper, см. ниже).
2) Экспортируй ключ:
   ```bash
   export OPENAI_API_KEY=sk-...
   # опционально, если уже есть Vector Store
   # export OPENAI_VECTOR_STORE_ID=vs_...
   ```
3) Запуск:
   ```bash
   ./gradlew bootRun
   ```

4) Проверка (передаём описание задачи — нарратив генерируется автоматически):
   ```bash
   curl -X POST http://localhost:8080/workflow/run \
     -H "Content-Type: application/json" \
     -d '{
       "goal": "Запуск проверки нового контрагента и уведомление заинтересованных сторон",
       "task": "Автоматизировать процесс KYC: заявка, сбор документов, дубли, чек-листы, уведомления",
       "constraints": { "maxIterations": 6 }
     }'
   ```

## Запуск в Docker / Render.com

1. Собрать образ локально:
   ```bash
   docker build -t iconix-agents .
   docker run --rm -p 8080:8080 -e OPENAI_API_KEY=sk-... iconix-agents
   ```
2. Для Render.com выбери **Docker** в качестве способа деплоя и укажи репозиторий.

## REST API для публичного доступа к агентам

Для публичного доступа к агентам используйте REST API endpoints:

- `GET /api/agents` - получить список доступных агентов
- `POST /api/agents/{agentName}` - вызвать конкретного агента
- `POST /api/agents/jsonrpc` - JSON-RPC 2.0 endpoint (опционально)

**Ключевое отличие от `/workflow/run`:**
- `/workflow/run` - жесткий план: narrative → model → review → usecase → mvc → scenario
- `/api/agents/{agentName}` - гибкий вызов любого агента независимо с передачей необходимых данных

**⚠️ Примечание:** Это REST API, а не полноценный MCP протокол. Для настоящего MCP (stdio transport) потребуется дополнительная реализация.

Подробная документация и примеры использования в файле [MCP.MD](MCP.MD).
3. Добавь переменные окружения:
   - `OPENAI_API_KEY` — обязательна;
   - `OPENAI_VECTOR_STORE_ID` — если нужно подключить готовый Vector Store.
4. Render автоматически выполнит `docker build` и запустит контейнер с `java -jar app.jar`.

## gradle wrapper (если нужен)
Если у тебя установлен локальный Gradle, можешь добавить wrapper в проект:
```bash
gradle wrapper
```
После этого можно запускать `./gradlew bootRun` без предварительно установленного Gradle.
