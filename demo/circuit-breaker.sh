#!/usr/bin/env bash
set -uo pipefail
BASE="${BASE:-http://localhost:8080}/api/v1"
J='Content-Type: application/json'

USER_ID=$(curl -fsS -X POST "$BASE/users" -H "$J" \
  -d '{"username":"cb-'"$RANDOM"'","email":"cb-'"$RANDOM"'@example.com","role":"USER_FREE","balance":"10.00"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
BODY='{"userId":"'"$USER_ID"'","serviceId":"00000000-0000-0000-0000-000000000000","victimContact":"demo@example.com","messageCount":1}'

send() { curl -sS -o /dev/null -w "POST /spam/send -> HTTP %{http_code} за %{time_total}с\n" -X POST "$BASE/spam/send" -H "$J" -d "$BODY"; }

echo "=== catalog-service работает: неизвестный сервис даёт 404"
send

echo "=== останавливаем catalog-service: dispatch-service отвечает 503 через fallback"
docker compose stop catalog-service >/dev/null
for _ in 1 2 3 4 5 6 7 8; do send; done

echo "=== user-service при этом работает"
curl -sS -o /dev/null -w "GET /users/{id} -> HTTP %{http_code}\n" "$BASE/users/$USER_ID"

echo "=== запускаем catalog-service обратно"
docker compose start catalog-service >/dev/null
until [ "$(docker compose ps --format '{{.Health}}' catalog-service)" = "healthy" ]; do sleep 3; done
echo "Сервис здоров. Через 30-60 секунд Eureka обновит реестр, а breaker закроется:"
for _ in $(seq 1 30); do
  code=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$BASE/spam/send" -H "$J" -d "$BODY")
  echo "HTTP $code"
  [ "$code" = "404" ] && break
  sleep 5
done
