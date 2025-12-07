# Интеграция LibreChat

## Варианты подключения

### Вариант 1: LibreChat через iframe (рекомендуется)

LibreChat - это полноценное standalone приложение. Самый простой способ - запустить его отдельно и встроить через iframe.

#### Шаги установки:

1. **Клонировать LibreChat:**
```bash
git clone https://github.com/danny-avila/LibreChat.git
cd LibreChat
```

2. **Настроить .env файл:**
Создайте `.env` файл с настройками (см. документацию LibreChat)

3. **Запустить через Docker:**
```bash
docker-compose up -d
```

4. **LibreChat будет доступен на:** `http://localhost:3080`

5. **В нашем приложении:** Страница `/chat.html` уже настроена на подключение через iframe к `http://localhost:3080`

**Преимущества:**
- Полный функционал LibreChat
- Поддержка множества провайдеров LLM
- История чатов
- Настройки моделей

---

### Вариант 2: Простой чат через Spring AI

Я создал простой чат-интерфейс, который использует Spring AI напрямую. Для этого нужно:

1. **Создать ChatController:**
```java
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatClient chatClient;
    
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String response = chatClient.prompt()
            .user(request.message())
            .call()
            .content();
        return new ChatResponse(response);
    }
}
```

2. **Создать DTO:**
```java
public record ChatRequest(String message, List<Map<String, String>> history) {}
public record ChatResponse(String response) {}
```

3. **Chat будет доступен на странице:** `/chat.html`

**Преимущества:**
- Интеграция в приложение
- Не требует дополнительных зависимостей
- Простота настройки

**Недостатки:**
- Ограниченный функционал по сравнению с LibreChat
- Нет истории между сессиями

---

### Вариант 3: Proxy для LibreChat API

Если хотите использовать LibreChat API через ваш бэкенд (для безопасности/контроля доступа):

1. **Добавить proxy endpoint в Spring:**
```java
@GetMapping("/librechat/**")
public ResponseEntity<?> proxyLibreChat(HttpServletRequest request) {
    // Перенаправление запросов к LibreChat API
}
```

2. **Или использовать Spring Cloud Gateway** для более сложной маршрутизации

---

## Рекомендация

**Используйте Вариант 1 (iframe)** - это самый простой и функциональный способ. LibreChat уже имеет все необходимые функции, и вам не нужно их переписывать.

**Для Варианта 2** - если нужен простой чат без дополнительных функций, можно использовать Spring AI напрямую (нужно создать контроллер, см. выше).

## Текущая реализация

Сейчас в `/chat.html` реализованы оба варианта:
- **Простой чат** - готов, но требует создания `/api/chat` endpoint
- **LibreChat iframe** - готов, но требует запуска LibreChat на порту 3080

Переключение между режимами доступно через радиокнопки на странице.

