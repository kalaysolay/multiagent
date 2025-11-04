# ICONIX Agents (Spring Boot + Spring AI)

Минимальный проект с эндпоинтом `/workflow/run`:
- Оркестратор вызывает два сервиса: `DomainModellerService` и `EvaluatorService` (обычные Spring `@Service`).
- 2 итерации: моделирование → оценка → доработка → повторная оценка.
- На выходе: итоговый PlantUML и список замечаний, закрытых между итерациями.
- Хранилище артефактов — OpenAI Files + Vector Store (по желанию).

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

4) Проверка:
   ```bash
   curl -X POST http://localhost:8080/workflow/run      -H "Content-Type: application/json"      -d '{"narrative":"Пользователь создаёт обращение на проверку контрагента, прикладывает файл, указывает ИНН, менеджер комплаенс просматривает и запрашивает дополнительные документы, затем принимает решение и уведомляет пользователя."}'
   ```

## gradle wrapper (если нужен)
Если у тебя установлен локальный Gradle, можешь добавить wrapper в проект:
```bash
gradle wrapper
```
После этого можно запускать `./gradlew bootRun` без предварительно установленного Gradle.
