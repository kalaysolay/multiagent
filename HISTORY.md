# История изменений проекта

## 2026-02-09 — Рефакторинг сайдбара в отдельный компонент

### Описание изменений
Вынесен сайдбар (боковое меню) в отдельный JavaScript модуль `sidebar.js` для устранения дублирования HTML кода на каждой странице. Теперь меню генерируется централизованно, что упрощает поддержку и добавление новых разделов. Раздел "Администрирование" теперь автоматически отображается на всех страницах для администраторов.

### Проблема
- HTML сайдбара дублировался на каждой странице (7 файлов)
- Раздел "Администрирование" был только в `index.html` и `prompts.html`
- При добавлении новых разделов нужно было обновлять все страницы
- Активный пункт меню определялся вручную на каждой странице

### Решение
Создан модуль `sidebar.js`, который:
- Динамически генерирует HTML сайдбара для всех страниц
- Автоматически определяет активный пункт меню на основе текущего URL
- Показывает/скрывает раздел "Администрирование" в зависимости от прав пользователя
- Содержит подробные комментарии для понимания джуниор-разработчиками

### Новые файлы

| Файл | Описание |
|------|----------|
| `src/main/resources/static/js/sidebar.js` | Модуль для динамической генерации сайдбара. Содержит конфигурацию меню, функции для определения активного пункта, генерации HTML и инициализации. |

### Изменённые файлы

| Файл | Что изменено |
|------|-------------|
| `src/main/resources/static/index.html` | Статический HTML сайдбара заменён на placeholder `<div id="sidebar-container"></div>`. Добавлен скрипт `sidebar.js`. Удалён inline скрипт для показа/скрытия раздела администрирования. |
| `src/main/resources/static/prompts.html` | Аналогично `index.html` |
| `src/main/resources/static/chat.html` | Аналогично `index.html` |
| `src/main/resources/static/iconix-agent-list.html` | Аналогично `index.html` |
| `src/main/resources/static/iconix-agent-detail.html` | Аналогично `index.html` |
| `src/main/resources/static/git-analyser.html` | Аналогично `index.html` |
| `src/main/resources/static/render.html` | Изменён layout с `container` на `app-container`. Добавлен placeholder для сайдбара и подключён `sidebar.js`. Добавлен `portal.css` для стилей сайдбара. |

### Преимущества
- Единая точка управления сайдбаром — все изменения в одном файле
- Раздел администрирования автоматически появляется на всех страницах для админов
- При добавлении новых разделов достаточно обновить только `sidebar.js`
- Автоматическое определение активного пункта меню на основе URL
- Устранено дублирование кода — уменьшение размера HTML файлов
- Улучшена поддерживаемость кода

### Технические детали
- Модуль использует IIFE (Immediately Invoked Function Expression) для изоляции области видимости
- Ожидает загрузки `auth.js` перед инициализацией для проверки прав администратора
- Определение активного пункта меню основано на сравнении `window.location.pathname` с массивом путей из конфигурации
- Конфигурация меню хранится в объекте `menuConfig` с разделами: documentation, agents, chats, admin

---

## 2026-02-09 — Справочник PROMPTS + флаг администратора

### Описание изменений
Все системные промпты, ранее захардкоженные в Java-коде и `.st`-файлах, перенесены в таблицу `prompts` в БД. Добавлена таблица `prompts_history` для хранения истории изменений. В таблицу `users` добавлен флаг `is_admin`. Создана админская страница «Промпты» с возможностью просмотра и редактирования промптов. Все изменения покрыты юнит-тестами.

### Миграции БД

| Миграция | Описание |
|----------|----------|
| `V7__add_is_admin_to_users.sql` | Добавляет колонку `is_admin BOOLEAN NOT NULL DEFAULT false` в `users`. Устанавливает `is_admin = true` для пользователя `admin`. |
| `V8__create_prompts_tables.sql` | Создаёт таблицы `prompts` (id, code, name, content, description, updated_at, updated_by) и `prompts_history` (id, prompt_id, content, changed_at, changed_by, change_reason). |

### Новые классы

#### Entity

| Класс | Описание |
|-------|----------|
| `com.example.portal.prompt.entity.Prompt` | Сущность промпта: code (уникальный), name, content, description, updated_at, updated_by |
| `com.example.portal.prompt.entity.PromptHistory` | Запись истории: prompt_id, content (старый текст), changed_at, changed_by, change_reason |

#### Repositories

| Класс | Описание |
|-------|----------|
| `com.example.portal.prompt.repository.PromptRepository` | JPA-репозиторий: `findByCode(code)`, `existsByCode(code)` |
| `com.example.portal.prompt.repository.PromptHistoryRepository` | JPA-репозиторий: `findByPromptIdOrderByChangedAtDesc(promptId)` |

#### Service

| Класс | Описание |
|-------|----------|
| `com.example.portal.prompt.service.PromptService` | `getByCode(code)` — с in-memory кэшем (ConcurrentHashMap); `getAll()`; `updatePrompt(code, newContent, userId, reason)` — сохраняет старую версию в history, инвалидирует кэш; `getHistory(code)` |

#### Controller

| Класс | Описание |
|-------|----------|
| `com.example.portal.prompt.controller.PromptController` | `GET /api/prompts` — список (admin); `GET /api/prompts/{code}` — один (admin); `PUT /api/prompts/{code}` — обновление с историей (admin); `GET /api/prompts/{code}/history` — история (admin) |

#### Initializer

| Класс | Описание |
|-------|----------|
| `com.example.portal.prompt.init.PromptDataInitializer` | `ApplicationRunner`: при старте сидирует 10 промптов (если их ещё нет в БД). Промпты из inline Java-кода и `.st`-файлов. |

### Изменённые классы

| Класс | Что изменено |
|-------|-------------|
| `User.java` | Добавлено поле `Boolean isAdmin` |
| `SecurityConfig.java` | `UserDetailsService` назначает `ROLE_ADMIN` / `ROLE_USER` на основе `is_admin`; `prompts.html` добавлена в permitted paths |
| `AuthController.java` | `POST /api/auth/login` и `GET /api/auth/me` теперь возвращают `isAdmin` |
| `DomainModellerService.java` | Промпты загружаются через `PromptService` вместо `private static final String` |
| `EvaluatorService.java` | Промпты загружаются через `PromptService` |
| `NarrativeWriterService.java` | Промпт загружается через `PromptService` |
| `MVCModellerService.java` | Промпт загружается через `PromptService` вместо `@Value("classpath:prompts/...")` |
| `ScenarioWriterService.java` | Промпт загружается через `PromptService` вместо `@Value` |
| `UseCaseModellerService.java` | Промпт загружается через `PromptService` вместо `@Value` |

### Фронтенд

| Файл | Что изменено |
|------|-------------|
| `login.html` | Сохраняет `isAdmin` в `localStorage` после логина |
| `js/auth.js` | Добавлена функция `auth.isAdmin()`; `checkAuth()` обновляет `isAdmin` из `/api/auth/me`; `logout()` очищает `isAdmin` |
| `index.html` | В сайдбаре добавлена секция «Администрирование → Промпты» (отображается только для `isAdmin`) |
| `prompts.html` (новый) | Страница управления промптами: таблица + модальное окно редактирования |
| `js/prompts.js` (новый) | Логика загрузки списка, открытия редактора, сохранения с причиной изменения |

### Коды промптов (10 шт.)

| Код | Сервис | Описание |
|-----|--------|----------|
| `domain_modeller_system` | DomainModellerService | Системный промпт (роль + правила построения модели) |
| `domain_modeller_generate` | DomainModellerService | User-промпт для генерации PlantUML |
| `domain_modeller_refine` | DomainModellerService | User-промпт для уточнения модели |
| `evaluator_plantuml` | EvaluatorService | Ревью PlantUML-модели |
| `evaluator_narrative` | EvaluatorService | Ревью нарратива |
| `narrative_writer` | NarrativeWriterService | Генерация нарратива |
| `mvc_modeller` | MVCModellerService | Диаграмма пригодности (Robustness) |
| `scenario_writer` | ScenarioWriterService | Сценарий по Коберну |
| `usecase_modeller` | UseCaseModellerService | Диаграмма прецедентов |
| `orchestrator_plan` | OrchestratorService | План агента-оркестратора |

### Тесты

| Файл | Описание |
|------|----------|
| `PromptServiceTest.java` | Юнит-тесты: getByCode (из БД + из кэша), getAll, updatePrompt (история + инвалидация кэша), clearCache, обработка ошибок. Mockito. |
| `PromptControllerTest.java` | WebMvcTest: GET/PUT с admin, GET/PUT с обычным пользователем (403), 401 для неаутентифицированных. |

### Зависимости

| Зависимость | Назначение |
|-------------|------------|
| `spring-security-test` (testImplementation) | Для `@WithMockUser` и `csrf()` в тестах контроллера |

---

## 2025-01-20 14:30:00 - Исправлена обработка ошибок аутентификации

### Описание изменений
Исправлена проблема, когда неаутентифицированные пользователи не перенаправлялись на страницу входа, а API запросы возвращали 401 без обработки.

### Внесенные изменения

#### 1. SecurityConfig.java
- Добавлен `AuthenticationEntryPoint` для обработки ошибок аутентификации
- Для API запросов возвращается JSON с ошибкой 401
- Для HTML запросов выполняется перенаправление на `/login.html`
- Добавлены импорты: `AuthenticationEntryPoint`, `HttpServletRequest`, `HttpServletResponse`, `AuthenticationException`

#### 2. auth.js
- Реализован перехват всех `fetch()` запросов через переопределение глобального `window.fetch`
- Автоматическое добавление JWT токена ко всем API запросам (начинающимся с `/api/`, `/workflow/`, `/render/`, `/chat/`, `/git/`, `/vector-store/`)
- Автоматическая обработка 401 ошибок с перенаправлением на страницу входа
- Улучшена проверка валидности токена при загрузке страницы
- Сохранена обратная совместимость через `window.auth.fetch()`

### Результат
- Все API запросы автоматически получают JWT токен из localStorage
- При получении 401 пользователь автоматически перенаправляется на страницу входа
- Неаутентифицированные пользователи при обращении к защищенным страницам перенаправляются на `/login.html`
- Улучшена обработка ошибок аутентификации на клиенте и сервере

## 2025-01-20 12:00:00 - Добавлена система аутентификации и авторизации

### Описание изменений
Реализована полная система аутентификации и авторизации на основе Spring Security и JWT токенов.

### Добавленные зависимости
- `spring-boot-starter-security` - Spring Security для аутентификации
- `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` - библиотеки для работы с JWT токенами
- `spring-security-crypto` - для хеширования паролей (BCrypt)

### Созданные компоненты

#### 1. Entity и Repository
- `com.example.portal.auth.entity.User` - сущность пользователя с полями: id, username, password, enabled, createdAt
- `com.example.portal.auth.repository.UserRepository` - репозиторий для работы с пользователями

#### 2. База данных
- `V6__create_users_table.sql` - Flyway миграция для создания таблицы users
  - Таблица содержит поля: id (UUID), username (уникальный), password (BCrypt хеш), enabled, created_at
  - По умолчанию создается пользователь `admin` с паролем `admin`
  - Пароль захеширован с помощью BCrypt (стоимость 10)

#### 3. JWT утилиты
- `com.example.portal.auth.config.JwtTokenProvider` - класс для генерации и валидации JWT токенов
  - Генерирует токены с username и userId
  - Время жизни токена: 24 часа (настраивается через `jwt.expiration`)
  - Секретный ключ настраивается через `jwt.secret` (по умолчанию используется дефолтный ключ)

#### 4. Spring Security конфигурация
- `com.example.portal.auth.config.SecurityConfig` - основная конфигурация безопасности
  - Настроена stateless аутентификация (без сессий)
  - Публичные эндпоинты: `/login.html`, `/api/auth/login`, статические ресурсы (css, js, images), HTML страницы
  - Все остальные запросы требуют аутентификации
  - Настроен CORS для работы с фронтендом

#### 5. JWT фильтр
- `com.example.portal.auth.filter.JwtAuthenticationFilter` - фильтр для проверки JWT токенов
  - Извлекает токен из заголовка `Authorization: Bearer <token>`
  - Валидирует токен и устанавливает аутентификацию в SecurityContext

#### 6. Контроллер аутентификации
- `com.example.portal.auth.controller.AuthController` - REST контроллер для аутентификации
  - `POST /api/auth/login` - эндпоинт для входа (принимает username и password, возвращает JWT токен)
  - `GET /api/auth/me` - эндпоинт для проверки текущего пользователя

#### 7. Фронтенд
- `login.html` - страница входа в систему
  - Дизайн: слева логотип, справа форма с полями логина и пароля
  - Автоматическая проверка наличия токена при загрузке
  - Сохранение токена в localStorage после успешного входа
  - Обработка ошибок входа

- `js/auth.js` - JavaScript утилита для работы с аутентификацией
  - `auth.getToken()` - получение токена из localStorage
  - `auth.isAuthenticated()` - проверка наличия токена
  - `auth.getAuthHeaders()` - получение заголовков с токеном для API запросов
  - `auth.fetch()` - обертка над fetch для автоматической подстановки токена
  - `auth.logout()` - выход из системы
  - `auth.checkAuth()` - автоматическая проверка аутентификации при загрузке страницы

### Особенности реализации
1. **Без регистрации**: Пользователи создаются только через базу данных, регистрация через UI не предусмотрена
2. **Дефолтный пользователь**: При первом запуске создается пользователь `admin` с паролем `admin`
3. **JWT токены**: Используются для stateless аутентификации, токены хранятся в localStorage браузера
4. **Защита API**: Все API эндпоинты (кроме `/api/auth/login`) требуют валидный JWT токен в заголовке Authorization
5. **Автоматическое перенаправление**: Неаутентифицированные пользователи автоматически перенаправляются на страницу входа

### Конфигурация
В `application.yml` можно настроить:
- `jwt.secret` - секретный ключ для подписи JWT токенов (рекомендуется изменить в продакшене)
- `jwt.expiration` - время жизни токена в миллисекундах (по умолчанию 86400000 = 24 часа)

### Использование
1. При первом запуске приложения создается пользователь `admin` с паролем `admin`
2. При обращении к защищенным страницам неаутентифицированный пользователь перенаправляется на `/login.html`
3. После успешного входа токен сохраняется в localStorage и автоматически добавляется ко всем API запросам
4. Для выхода из системы можно вызвать `auth.logout()` или удалить токен из localStorage
