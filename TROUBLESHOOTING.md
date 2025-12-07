# Устранение ошибок OpenAI API

## Ошибка 401 Unauthorized

### Проблема
```
401 Unauthorized from POST https://api.openai.com/v1/vector_stores/vs_68e649a20d0c8191aaa619e4edeeb03a/search
```

### Решения

1. **Проверьте API ключ**:
   - Убедитесь, что ключ задан через переменную окружения `OPENAI_API_KEY`
   - Проверьте, что ключ валиден на https://platform.openai.com/api-keys
   - Убедитесь, что ключ не истек и не был отозван

2. **Проверьте доступ к Vector Store**:
   - Vector Store должен существовать и быть доступен с вашим API ключом
   - Проверьте ID Vector Store: `vs_68e649a20d0c8191aaa619e4edeeb03a`
   - Если Vector Store недоступен, можно временно отключить RAG:
     ```yaml
     app:
       openai:
         vector-store-id: ${OPENAI_VECTOR_STORE_ID:}  # Пустое значение отключит RAG
     ```

3. **Проверьте модель**:
   - Текущая модель: `gpt-4-turbo-preview`
   - Убедитесь, что модель доступна для вашего API ключа
   - Если модель недоступна, попробуйте `gpt-4` или `gpt-3.5-turbo`

### Временное решение

Если проблема с Vector Store, можно временно отключить RAG. Workflow будет работать без RAG контекста:

```yaml
app:
  openai:
    vector-store-id: ${OPENAI_VECTOR_STORE_ID:}  # Пустое значение
```

При пустом `vector-store-id` RAG сервис вернет пустой контекст, но workflow продолжит работу.

## Ошибка извлечения ответа

Эта ошибка обычно следует за 401 - если API ключ неверен, OpenAI возвращает ошибку вместо JSON.

### Решение
Исправьте проблему с API ключом (см. выше).

## Важно

Ошибка **НЕ связана** с ChatController или LibreChat. Это общая проблема конфигурации OpenAI API, которая влияет на все сервисы.

