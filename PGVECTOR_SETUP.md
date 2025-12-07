# Установка pgvector для PostgreSQL

Если вы получаете ошибку при запуске миграции `V2__create_vector_store.sql`, это означает, что расширение `pgvector` не установлено в вашем PostgreSQL.

## Что такое pgvector?

`pgvector` - это расширение для PostgreSQL, которое позволяет хранить и искать векторные данные (эмбеддинги) напрямую в базе данных.

## Способы установки

### Вариант 1: Docker (Рекомендуется)

Используйте образ PostgreSQL с предустановленным pgvector:

```bash
docker run -d \
  --name postgres-pgvector \
  -e POSTGRES_PASSWORD=123qweasd \
  -e POSTGRES_DB=iconix_agent_db \
  -p 5432:5432 \
  ankane/pgvector:latest
```

Или в `docker-compose.yml`:

```yaml
version: '3.8'
services:
  postgres:
    image: ankane/pgvector:latest
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: 123qweasd
      POSTGRES_DB: iconix_agent_db
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

### Вариант 2: Установка в существующий PostgreSQL (Linux)

1. Установите зависимости:
```bash
sudo apt-get install build-essential postgresql-server-dev-XX
# где XX - версия PostgreSQL (например, 14, 15, 16)
```

2. Скачайте и установите pgvector:
```bash
git clone --branch v0.5.1 https://github.com/pgvector/pgvector.git
cd pgvector
make
sudo make install
```

3. Подключитесь к PostgreSQL и создайте расширение:
```bash
psql -U postgres -d iconix_agent_db
CREATE EXTENSION vector;
```

### Вариант 3: Windows

1. Скачайте готовые файлы с https://github.com/pgvector/pgvector/releases
2. Скопируйте файлы в директорию PostgreSQL:
   - `vector.dll` → `C:\Program Files\PostgreSQL\XX\lib\`
   - `vector.control` → `C:\Program Files\PostgreSQL\XX\share\extension\`
   - `vector--*.sql` → `C:\Program Files\PostgreSQL\XX\share\extension\`
3. Перезапустите PostgreSQL
4. Подключитесь и создайте расширение:
```sql
CREATE EXTENSION vector;
```

### Вариант 4: Использовать без локального хранилища (временно)

Если вы не можете установить pgvector прямо сейчас, вы можете использовать OpenAI Vector Store:

В `application.yml`:
```yaml
app:
  vector-store-provider: OPENAI  # Используйте OpenAI вместо локального хранилища
```

Миграция не будет выполняться, если pgvector не установлен (благодаря улучшенной обработке ошибок).

## Проверка установки

После установки проверьте, что расширение работает:

```sql
-- Подключитесь к базе данных
psql -U postgres -d iconix_agent_db

-- Проверьте, установлено ли расширение
SELECT * FROM pg_extension WHERE extname = 'vector';

-- Если расширение установлено, вы увидите строку с информацией о нём
```

## Дополнительные ресурсы

- Официальный репозиторий: https://github.com/pgvector/pgvector
- Документация: https://github.com/pgvector/pgvector#installation

