# Исправление конфигурации OpenAI API

## Проблема

Ошибка `401 Unauthorized` при работе с OpenAI API. Это **НЕ связано** с ChatController или LibreChat.

## Причины

1. **API ключ** - возможно, недействителен или истек
2. **Vector Store** - возможно, недоступен с текущим API ключом
3. **Модель** - `gpt-4-turbo-preview` может быть недоступна

## Решения

### 1. Проверьте API ключ

Убедитесь, что:
- Ключ задан через переменную окружения: `OPENAI_API_KEY`
- Ключ валиден и активен
- Ключ имеет доступ к используемым моделям

### 2. Временно отключите Vector Store

Я обновил `application.yml` - теперь Vector Store ID по умолчанию пустой:
```yaml
app:
  openai:
    vector-store-id: ${OPENAI_VECTOR_STORE_ID:}
```

Это означает, что RAG будет пропущен, но workflow продолжит работу. Чтобы включить RAG, установите переменную окружения `OPENAI_VECTOR_STORE_ID`.

### 3. Используйте правильную модель

Если `gpt-4-turbo-preview` недоступна, попробуйте:
- `gpt-4` 
- `gpt-3.5-turbo`
- `gpt-4o` (если доступна)

Задайте через переменную окружения:
```bash
export OPENAI_MODEL=gpt-4
```

## Текущая конфигурация

- API ключ: `${OPENAI_API_KEY:}` (обязательно через переменную окружения)
- Модель: `${OPENAI_MODEL:gpt-4-turbo-preview}` (можно переопределить)
- Vector Store: `${OPENAI_VECTOR_STORE_ID:}` (по умолчанию пустой - RAG отключен)

## Тестирование

1. Установите API ключ:
   ```bash
   export OPENAI_API_KEY=sk-your-key-here
   ```

2. (Опционально) Установите модель:
   ```bash
   export OPENAI_MODEL=gpt-4
   ```

3. (Опционально) Установите Vector Store ID, если нужен RAG:
   ```bash
   export OPENAI_VECTOR_STORE_ID=vs_...
   ```

4. Перезапустите приложение

## Важно

- Ошибка не связана с ChatController
- ChatController использует тот же ChatClient, что и все остальные сервисы
- Проблема в конфигурации OpenAI API

