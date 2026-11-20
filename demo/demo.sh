#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE:-http://localhost:8080}/api/v1"
COUNT="${1:-${COUNT:-3}}"
PRICE=2
BALANCE=$(( COUNT * PRICE + 50 ))
SUPERVISOR_USERNAME="${SUPERVISOR_USERNAME:-supervisor}"
SUPERVISOR_PASSWORD="${SUPERVISOR_PASSWORD:-supervisor-pass}"
PASS="demo-password-1"
J='Content-Type: application/json'

field() { python3 -c "import sys,json; print(json.load(sys.stdin)['$1'])"; }
say()   { printf '\n=== %s ===\n' "$1"; }
login() {
  curl -fsS -X POST "$BASE/auth/login" -H "$J" \
    -d '{"username":"'"$1"'","password":"'"$2"'"}' | field accessToken
}

say "health"
curl -fsS "${BASE%/api/v1}/actuator/health"; echo

say "вход супервайзера ($SUPERVISOR_USERNAME)"
SUP=$(login "$SUPERVISOR_USERNAME" "$SUPERVISOR_PASSWORD")
echo "токен получен: ${SUP:0:24}..."

say "супервайзер создаёт клиента USER_PRO (balance=$BALANCE) и оператора закупок"
CLIENT="demo-$RANDOM"
OPERATOR="op-$RANDOM"
curl -fsS -X POST "$BASE/users" -H "$J" -H "Authorization: Bearer $SUP" \
  -d '{"username":"'"$CLIENT"'","email":"'"$CLIENT"'@example.com","role":"USER_PRO","balance":"'"$BALANCE"'.00","password":"'"$PASS"'"}'
echo "   <- в ответе нет пароля"
curl -fsS -X POST "$BASE/users" -H "$J" -H "Authorization: Bearer $SUP" \
  -d '{"username":"'"$OPERATOR"'","email":"'"$OPERATOR"'@example.com","role":"BUY_OPERATOR","password":"'"$PASS"'"}' >/dev/null
echo "оператор $OPERATOR создан"

say "оператор входит и заводит сервис (цена $PRICE) и прокси"
OP=$(login "$OPERATOR" "$PASS")
SVC_ID=$(curl -fsS -X POST "$BASE/services" -H "$J" -H "Authorization: Bearer $OP" \
  -d '{"name":"bulk-'"$RANDOM"'","description":"demo","pricePerMessage":"'"$PRICE"'.0000"}' | field id)
curl -fsS -X POST "$BASE/proxy" -H "$J" -H "Authorization: Bearer $OP" \
  -d '{"host":"10.0.0.1","port":8080,"protocol":"HTTP","status":"ACTIVE"}' >/dev/null
echo "сервис $SVC_ID, прокси добавлен"

say "клиент входит и отправляет $COUNT сообщ. со своего баланса (userId не передаёт)"
USR=$(login "$CLIENT" "$PASS")
curl -fsS -X POST "$BASE/spam/send" -H "$J" -H "Authorization: Bearer $USR" \
  -d '{"serviceId":"'"$SVC_ID"'","victimContact":"demo@example.com","messageBody":"hello from lab","messageCount":'"$COUNT"'}' \
  | python3 -m json.tool

say "баланс клиента после отправки (должен стать $(( BALANCE - COUNT * PRICE )))"
curl -fsS "$BASE/users/me" -H "Authorization: Bearer $USR" \
  | python3 -c "import sys,json; print('balance =', json.load(sys.stdin)['balance'])"

echo
echo "Смотри лог приёмника: там должно появиться сообщений: $COUNT."
echo "Проверки запретов по ролям: ./demo/roles.sh"
