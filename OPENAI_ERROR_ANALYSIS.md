# Анализ ошибки OpenAI API

## Ошибка

```
401 Unauthorized from POST https://api.openai.com/v1/vector_stores/vs_68e649a20d0c8191aaa619e4edeeb03a/search
Error while extracting response for type [org.springframework.ai.openai.api.OpenAiApi$ChatCompletion] and content type [text/plain]
```

## Причины

1. **401 Unauthorized** - Проблема с аутентификацией OpenAI API:
   - Неправильный API ключ
   - API ключ истек или отозван
   - API ключ не имеет доступа к Vector Store API

2. **Ошибка извлечения ответа** - OpenAI API вернул ошибку вместо ожидаемого JSON

## Решения

### 1. Проверьте API ключ

Убедитесь, что:
- API ключ валиден и активен
- Ключ имеет доступ к используемым моделям (gpt-4-turbo-preview)
- Ключ имеет доступ к Vector Store API (для RAG)

### 2. Проверьте Vector Store ID

Убедитесь, что:
- Vector Store существует
- Vector Store доступен с вашим API ключом
- Vector Store ID правильный: `vs_68e649a20d0c8191aaa619e4edeeb03a`

### 3. Временное решение - отключить Vector Store

Если проблема с Vector Store, можно временно отключить RAG:
- Удалите или закомментируйте использование `OpenAiRagService` в worker'ах
- Или установите `OPENAI_VECTOR_STORE_ID=` (пустое значение)

### 4. Использовать другую модель

Если проблема с моделью, попробуйте:
- `gpt-4` или `gpt-3.5-turbo`
- Проверьте доступность модели для вашего API ключа

## Проверка конфигурации

Убедитесь, что в `application.yml`:
- API ключ задан через переменную окружения: `${OPENAI_API_KEY:}`
- Модель корректна: `gpt-4-turbo-preview` (или другая доступная)
- Vector Store ID задан через переменную окружения: `${OPENAI_VECTOR_STORE_ID:}`

## Замечание

Ошибка не связана с ChatController или LibreChat. Это проблема конфигурации OpenAI API.

