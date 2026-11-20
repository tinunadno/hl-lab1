#!/usr/bin/env bash
# Показывает, что одно и то же действие разрешено или запрещено в зависимости от роли.
set -euo pipefail
BASE="${BASE:-http://localhost:8080}/api/v1"
SUPERVISOR_USERNAME="${SUPERVISOR_USERNAME:-supervisor}"
SUPERVISOR_PASSWORD="${SUPERVISOR_PASSWORD:-supervisor-pass}"
PASS="demo-password-1"
J='Content-Type: application/json'

field() { python3 -c "import sys,json; print(json.load(sys.stdin)['$1'])"; }
login() { curl -fsS -X POST "$BASE/auth/login" -H "$J" -d '{"username":"'"$1"'","password":"'"$2"'"}' | field accessToken; }
code()  { curl -sS -o /dev/null -w '%{http_code}' "$@"; }
check() { # check <ожидаемый код> <описание> curl-аргументы...
  local want="$1" what="$2"; shift 2
  local got; got=$(code "$@")
  printf '%-62s %s (ожидали %s)%s\n' "$what" "$got" "$want" "$([ "$got" = "$want" ] && echo '' || echo '  <-- !!!')"
}

SUP=$(login "$SUPERVISOR_USERNAME" "$SUPERVISOR_PASSWORD")
mk() { curl -fsS -X POST "$BASE/users" -H "$J" -H "Authorization: Bearer $SUP" \
  -d '{"username":"'"$1"'","email":"'"$1"'@example.com","role":"'"$2"'","balance":"100.00","password":"'"$PASS"'"}' | field id; }

FREE="free-$RANDOM";  FREE_ID=$(mk "$FREE" USER_FREE)
PRO="pro-$RANDOM";    PRO_ID=$(mk "$PRO" USER_PRO)
OPER="oper-$RANDOM";  mk "$OPER" BUY_OPERATOR >/dev/null
F=$(login "$FREE" "$PASS"); P=$(login "$PRO" "$PASS"); O=$(login "$OPER" "$PASS")
SVC=$(curl -fsS -X POST "$BASE/services" -H "$J" -H "Authorization: Bearer $SUP" \
  -d '{"name":"roles-'"$RANDOM"'","pricePerMessage":"1.0000"}' | field id)
send() { echo '{"serviceId":"'"$SVC"'","victimContact":"x@example.com","messageCount":'"$1"'}'; }
proxy='{"host":"10.0.0.9","port":3128,"protocol":"HTTP","status":"ACTIVE"}'

echo "--- аутентификация"
check 401 "без токена: GET /users/me"                      "$BASE/users/me"
check 401 "неверный пароль: POST /auth/login"              -X POST "$BASE/auth/login" -H "$J" -d '{"username":"'"$PRO"'","password":"wrong-password"}'

echo "--- пользователей создаёт только супервайзер"
check 403 "USER_PRO создаёт пользователя"                  -X POST "$BASE/users" -H "$J" -H "Authorization: Bearer $P" -d '{"username":"x'"$RANDOM"'","email":"x'"$RANDOM"'@e.com","role":"SUPERVISOR","password":"'"$PASS"'"}'
check 403 "BUY_OPERATOR смотрит список пользователей"      "$BASE/users" -H "Authorization: Bearer $O"
check 403 "USER_FREE смотрит чужой профиль"                "$BASE/users/$PRO_ID" -H "Authorization: Bearer $F"
check 403 "USER_FREE повышает себе роль"                   -X PATCH "$BASE/users/$FREE_ID" -H "$J" -H "Authorization: Bearer $F" -d '{"role":"SUPERVISOR"}'
check 200 "USER_FREE меняет свой пароль"                   -X PATCH "$BASE/users/$FREE_ID" -H "$J" -H "Authorization: Bearer $F" -d '{"password":"'"$PASS"'"}'

echo "--- оператор закупок не делает рассылки, клиенты не трогают прокси"
check 403 "BUY_OPERATOR: POST /spam/send"                  -X POST "$BASE/spam/send" -H "$J" -H "Authorization: Bearer $O" -d "$(send 1)"
check 201 "BUY_OPERATOR: POST /proxy"                      -X POST "$BASE/proxy" -H "$J" -H "Authorization: Bearer $O" -d "$proxy"
check 403 "USER_PRO: POST /proxy"                          -X POST "$BASE/proxy" -H "$J" -H "Authorization: Bearer $P" -d "$proxy"
check 403 "USER_PRO: POST /services"                       -X POST "$BASE/services" -H "$J" -H "Authorization: Bearer $P" -d '{"name":"n'"$RANDOM"'","pricePerMessage":"1"}'

echo "--- лимит зависит от роли, платить можно только своими деньгами"
check 403 "USER_FREE: 11 сообщений (лимит 10)"            -X POST "$BASE/spam/send" -H "$J" -H "Authorization: Bearer $F" -d "$(send 11)"
check 200 "USER_PRO: 11 сообщений"                         -X POST "$BASE/spam/send" -H "$J" -H "Authorization: Bearer $P" -d "$(send 11)"
check 403 "USER_FREE отправляет за счёт USER_PRO"          -X POST "$BASE/spam/send" -H "$J" -H "Authorization: Bearer $F" -d '{"userId":"'"$PRO_ID"'","serviceId":"'"$SVC"'","victimContact":"x@example.com","messageCount":1}'

echo "--- журнал: клиент видит только свои записи"
curl -fsS "$BASE/spam-log?size=50" -H "Authorization: Bearer $P" | python3 -c "
import sys,json; rows=json.load(sys.stdin)
print('записей у USER_PRO:', len(rows), '| все свои:', all(r['userId']=='$PRO_ID' for r in rows))"
