# Spamer Microservices — Лабораторная работа №2

Монолит из лабораторной №1 разделён на микросервисы на Spring Cloud. Это
**учебный проект**: CRUD, биллинг и реальная HTTP-отправка на **свой тестовый
приёмник**. Адрес приёмника берётся только из конфигурации оператора
(`SENDER_TARGET_URL`): тело запроса не может выбрать, куда пойдёт соединение.
Прокси остаются связью в БД, трафик через них не маршрутизируется.

## Архитектура

```mermaid
flowchart LR
    client([Клиент]) --> gw[api-gateway :8080<br/>Gateway, общий Swagger]
    gw --> us[user-service :8081<br/>Reactor + R2DBC]
    gw --> cs[catalog-service :8082<br/>Reactor + JPA]
    gw --> ds[dispatch-service :8083<br/>MVC + JPA]
    ds -- "Feign + Circuit Breaker" --> us
    ds -- "Feign + Circuit Breaker" --> cs
    us -- "Feign + Circuit Breaker" --> cs
    us --> db1[(spamer_users)]
    cs --> db2[(spamer_catalog)]
    ds --> db3[(spamer_dispatch)]
    ds --> rcv[Тестовый приёмник]
```

Все сервисы получают конфигурацию из `config-server` (:8888) и регистрируются в
`eureka-server` (:8761). Снаружи опубликованы только gateway, панель Eureka и
порт PostgreSQL.

| Модуль | Отвечает за | Стек |
|--------|-------------|------|
| `config-server` | раздаёт конфигурацию из отдельного git-репозитория `config-repo/` | Config Server, Git |
| `eureka-server` | реестр сервисов | Eureka Server |
| `api-gateway` | единая точка входа, общий Swagger, fallback | Spring Cloud Gateway |
| `user-service` | `users`, `user_service_subscriptions`, `balance_operations` | WebFlux, **Reactor + R2DBC** |
| `catalog-service` | `services` | WebFlux, **Reactor + Spring Data JPA** |
| `dispatch-service` | `proxy`, `spam_log`, `spam_log_proxy`, отправка | Spring MVC, JPA, Feign |
| `common` | формат ошибок, обработка ошибок Feign, настройки Circuit Breaker | библиотека |

### Выполнение требований

| Требование | Где реализовано |
|-----------|-----------------|
| Регистрация в Eureka | `eureka-server`, клиент во всех сервисах |
| Конфигурация из Config Server | отдельный репозиторий `config-repo/`, Git-бэкенд config-server, `spring.config.import` в клиентах |
| Доступ через Spring Gateway | маршруты в `config-repo/api-gateway/api-gateway.yml` |
| Feign Client | `dispatch-service/.../client`, `user-service/.../client` |
| Circuit Breaker | Resilience4j: Feign-клиенты (`FeignCircuitBreakerConfig`) и маршруты gateway |
| Reactor + R2DBC | `user-service` |
| Reactor + Spring Data JPA | `catalog-service`: JPA выполняется на `boundedElastic` (`ServiceCatalogService`) |

Вместо Hystrix из ссылки в задании используется Resilience4j: Hystrix удалён из
Spring Cloud, а это его штатная замена через Spring Cloud Circuit Breaker.

## Запуск

```bash
docker compose up --build
```

Первый запуск занимает несколько минут. Система готова, когда `docker compose ps`
показывает `healthy` у всех сервисов. Нужно примерно 3 ГБ свободной памяти.

- API и общий Swagger: http://localhost:8080/swagger-ui.html (сервис выбирается в списке вверху)
- Панель Eureka: http://localhost:8761

Основные переменные окружения:

| Переменная | Назначение |
|------------|------------|
| `DB_USERNAME`, `DB_PASSWORD` | учётные данные PostgreSQL |
| `SENDER_TARGET_URL` | адрес приёмника для `/spam/send` |
| `REFUND_RETRY_DELAY_MS` | интервал повтора незавершённых возвратов |

## Тесты

```bash
mvn verify                # нужен запущенный Docker (Testcontainers)
```

Порог покрытия JaCoCo — 70% по строкам в каждом доменном сервисе
(`user-service`, `catalog-service`, `dispatch-service`). Общий отчёт по всем
модулям: `target/site/jacoco/index.html`. Отчёт
отдельного модуля: `<модуль>/target/site/jacoco/index.html`.

- Модульные тесты: сага `SpamSendService`, возвраты, `SubscriptionService`, разбор ошибок Feign.
- Интеграционные тесты на реальном PostgreSQL: API всех сервисов.
- `CatalogClientCircuitBreakerTest`: настоящая цепочка Feign → балансировщик → HTTP
  → Circuit Breaker. Проверяет, что 404 не размыкает breaker, а после серии сбоев
  вызовы к сервису прекращаются.

## REST API

Все запросы идут через gateway на порт 8080 с префиксом `/api/v1`. Публичный API
не изменился по сравнению с лабораторной №1.

| Путь | Сервис |
|------|--------|
| `/users/**`, `/subscriptions` | user-service |
| `/services/**` | catalog-service |
| `/proxy/**`, `/spam-log/**`, `/spam/send`, `/test-receiver/inbox` | dispatch-service |

Изменился один случай: если нужный сервис недоступен, ответом будет `503` с
читаемым сообщением. Новые эндпоинты списания и возврата находятся в
`/internal/**`, gateway их не маршрутизирует.

Пагинация: `GET /users?page=&size=` отдаёт `X-Total-Count`,
`GET /spam-log/feed?cursor=&size=` работает как бесконечная прокрутка без общего
количества. `size` не больше 50.

## Что изменила декомпозиция

### Данные

У каждого сервиса своя база, поэтому внешние ключи между сервисами стали
логическими ссылками, которые проверяются через Feign при записи.

| Связь | Тип | Где теперь |
|-------|-----|-----------|
| `spam_log` ↔ `proxy` | Many-to-Many | внутри `dispatch-service`, настоящая связь JPA |
| `users` → подписки | One-to-Many | внутри `user-service`, настоящий внешний ключ |
| `users` ↔ `services` | Many-to-Many с доп. полями | `service_id` — логическая ссылка на `catalog-service` |
| `users` → `spam_log` | One-to-Many | `user_id` — логическая ссылка на `user-service` |
| `services` → `proxy` | One-to-Many | `provider_id` — логическая ссылка на `catalog-service` |

**Известное ограничение.** Каскадное удаление между сервисами больше не работает.
После удаления пользователя его записи `spam_log` остаются как журнал, а после
удаления сервиса остаются подписки и `provider_id` на него. Рассылка событий об
удалении естественно ложится на Kafka/RabbitMQ из лабораторной №4.

### Транзакции

**`POST /subscriptions` — локальная транзакция.** Подписка и роль пользователя
лежат в одной базе, поэтому остались атомарными: иначе возможны состояния «PRO без
подписки» или «подписка есть, а роль не сменилась». Запрос в каталог выполняется
до транзакции, чтобы медленный сервис не удерживал соединение с БД.

**`POST /spam/send` — сага.** Баланс и журнал теперь в разных базах, общая
транзакция невозможна:

1. Получить цену из `catalog-service`.
2. Создать в журнале запись `QUEUED`.
3. Списать деньги в `user-service`. Эта операция сама выполняется в транзакции
   (запись операции + изменение баланса).
4. Отправить сообщения на приёмник.
5. Успех: `SENT`. Ошибка доставки: `FAILED` и возврат денег.

Сбои обрабатываются так:

| Сбой | Что происходит |
|------|----------------|
| Мало денег или нет пользователя | запись журнала удаляется, `422` или `404` |
| Приёмник отказал | `FAILED`, деньги возвращаются, `422` |
| `user-service` недоступен при списании | неизвестно, прошло ли списание, поэтому отправляется возврат, `503` |
| `user-service` недоступен при возврате | флаг `refund_pending`, повтор по расписанию |

Повторы безопасны, потому что списание и возврат идемпотентны по `operationId`
(таблица `balance_operations`). Повторная операция ничего не меняет, а возврат
неизвестной операции оставляет запись с нулевой суммой, из-за которой опоздавшее
списание будет отклонено.

Как и в лабораторной №1, принятый приёмником запрос отменить нельзя: если сообщение
2 из N не дошло, деньги вернутся, но первое сообщение приёмник уже получил.

## Демо для защиты

```bash
./demo/demo.sh                # полный сценарий через gateway
./demo/circuit-breaker.sh     # остановка сервиса, 503 и восстановление
```

Приёмник на своём сервере:

```bash
PORT=9000 python3 demo/receiver.py                       # на сервере
SENDER_TARGET_URL=http://<IP>:9000/inbox docker compose up --build
```

## Структура

```
├── common/             # ошибки, поддержка Feign и Circuit Breaker
├── config-repo/        # отдельный git-репозиторий: папка на сервис и общий application.yml
├── config-server/      # читает config-repo по Git (file:// или CONFIG_REPO_URI)
├── eureka-server/
├── api-gateway/
├── user-service/       # controller, service, domain, dto, client
├── catalog-service/    # controller, service, domain, dto, mapper
├── dispatch-service/   # controller, service, domain, dto, mapper, client
├── docker/postgres/    # создание баз при первом запуске
└── demo/
```
