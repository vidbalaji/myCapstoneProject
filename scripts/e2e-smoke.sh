#!/usr/bin/env bash
# End-to-end check of every PRD flow, through the API Gateway, against a running stack:
#   docker compose up -d   then   ./scripts/e2e-smoke.sh
# Needs curl and jq. Exits non-zero on the first failed check.
set -uo pipefail

GW=${GATEWAY_URL:-http://localhost:8080}
NOTIFY=${NOTIFICATION_URL:-http://localhost:8086}
ADMIN_EMAIL=${ADMIN_EMAIL:-admin@capstone-shop.local}
RUN=$(date +%s)
CUSTOMER_EMAIL="customer.$RUN@example.com"
PASS=0

ok()   { PASS=$((PASS + 1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; [ -n "${2:-}" ] && printf '       %s\n' "$2"; exit 1; }
step() { printf '\n\033[1m%s\033[0m\n' "$1"; }

# call METHOD PATH [TOKEN] [JSON]  -> sets $STATUS, $BODY, $HEADERS
call() {
  local method=$1 path=$2 token=${3:-} data=${4:-}
  local args=(-s -X "$method" "$GW$path" -D /tmp/e2e_headers -o /tmp/e2e_body -w '%{http_code}')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' -d "$data")
  STATUS=$(curl "${args[@]}")
  BODY=$(cat /tmp/e2e_body)
  HEADERS=$(cat /tmp/e2e_headers)
}
expect() { [ "$STATUS" = "$1" ] && ok "$2 ($STATUS)" || fail "$2: expected $1, got $STATUS" "$BODY"; }

login() {
  call POST /auth/login "" "{\"email\":\"$1\",\"password\":\"$2\"}"
  echo "$HEADERS" | tr -d '\r' | awk -F'Bearer ' '/^[Aa]uthorization:/ {print $2}'
}

wait_for() {  # wait_for DESCRIPTION SECONDS COMMAND...
  local desc=$1 secs=$2; shift 2
  for _ in $(seq 1 "$secs"); do "$@" && { ok "$desc"; return; }; sleep 1; done
  fail "$desc (timed out after ${secs}s)" "$BODY"
}

step "1. User management (PRD 1.x, 6.x)"
call POST /auth/signup "" "{\"name\":\"Admin\",\"email\":\"$ADMIN_EMAIL\",\"password\":\"Admin@123\",\"phoneNumber\":\"9000000000\"}"
[[ "$STATUS" = 201 || "$STATUS" = 409 ]] && ok "admin account available ($STATUS)" || fail "admin signup" "$BODY"
call POST /auth/signup "" "{\"name\":\"Asha\",\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"Secret@123\",\"phoneNumber\":\"9$RUN\"}"
expect 201 "customer signup"
call POST /auth/signup "" "{\"name\":\"Asha\",\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"x\",\"phoneNumber\":\"8$RUN\"}"
expect 409 "duplicate email rejected"
ADMIN_TOKEN=$(login "$ADMIN_EMAIL" "Admin@123"); [ -n "$ADMIN_TOKEN" ] && ok "admin login returns token" || fail "admin login" "$BODY"
call POST /auth/login "" "{\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"wrong\"}"
expect 401 "wrong password rejected"
TOKEN=$(login "$CUSTOMER_EMAIL" "Secret@123"); [ -n "$TOKEN" ] && ok "customer login returns token" || fail "customer login" "$BODY"
call GET /users/me "$TOKEN"; expect 200 "view own profile"
USER_ID=$(echo "$BODY" | jq -r .id)
call PATCH /users/me "$TOKEN" '{"name":"Asha Kumar"}'; expect 200 "update profile"
[ "$(echo "$BODY" | jq -r .name)" = "Asha Kumar" ] && ok "profile name updated" || fail "profile name" "$BODY"

step "2. Gateway security"
call GET /cart; expect 401 "no token -> 401"
call GET /cart "not-a-real-token"; expect 401 "bad token -> 401"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' -H "X-User-Id: $USER_ID" "$GW/cart"); expect 401 "spoofed X-User-Id without token -> 401"
call POST /products "$TOKEN" '{"name":"Hack","price":1}'; expect 403 "customer cannot create products"
call GET /users/1 "$TOKEN"; expect 403 "customer cannot read other users"

step "3. Product catalog (PRD 2.x)"
call POST /categories "$ADMIN_TOKEN" "{\"name\":\"Laptops $RUN\",\"description\":\"Portable computers\"}"; expect 201 "admin creates category"
CATEGORY_ID=$(echo "$BODY" | jq -r .id)
call POST /products "$ADMIN_TOKEN" "{\"name\":\"ZenBook $RUN Laptop\",\"description\":\"14 inch OLED laptop with 16GB RAM\",\"price\":74999,\"imageUrl\":\"https://img.example/zen.png\",\"category\":{\"id\":$CATEGORY_ID}}"
expect 201 "admin creates product 1"; P1=$(echo "$BODY" | jq -r .id)
call POST /products "$ADMIN_TOKEN" "{\"name\":\"ThinkPad $RUN\",\"description\":\"Business laptop, 14 inch\",\"price\":89999,\"category\":{\"id\":$CATEGORY_ID}}"
expect 201 "admin creates product 2"; P2=$(echo "$BODY" | jq -r .id)
call GET "/products/$P1"; expect 200 "product details are public"
call GET "/categories/$CATEGORY_ID/products?page=0&size=10"; expect 200 "browse by category"
[ "$(echo "$BODY" | jq -r '.page.totalElements')" = 2 ] && ok "category has 2 products" || fail "category browse" "$BODY"
search_finds() { call GET "/search?q=$1&size=5"; echo "$BODY" | jq -e --argjson id "$P1" '.content[] | select(.id == $id)' >/dev/null; }
wait_for "keyword search finds new product (Elasticsearch)" 20 search_finds "ZenBook%20$RUN"
search_finds "zenbok%20$RUN" && ok "typo 'zenbok' still finds ZenBook (fuzzy search)" || fail "fuzzy search" "$BODY"

step "4. Cart (PRD 3.1, 3.2)"
call POST /cart/items "$TOKEN" "{\"productId\":$P1,\"quantity\":1}"; expect 200 "add product 1 to cart"
call POST /cart/items "$TOKEN" "{\"productId\":$P2,\"quantity\":2}"; expect 200 "add product 2 to cart"
call POST /cart/items "$TOKEN" '{"productId":999999999,"quantity":1}'; expect 404 "unknown product rejected"
call GET /cart "$TOKEN"; expect 200 "review cart"
[ "$(echo "$BODY" | jq -r .totalAmount)" = "254997.0" ] && ok "cart total = 74999 + 2 x 89999" || fail "cart total" "$BODY"

step "5. Checkout, payment and order management (PRD 3.3, 4.x, 5.x)"
ADDRESS='{"fullName":"Asha Kumar","line1":"12 MG Road","city":"Bengaluru","state":"KA","pincode":"560001","phoneNumber":"9000000001"}'
call POST /orders/checkout "$TOKEN" "{\"deliveryAddress\":$ADDRESS,\"paymentMethod\":\"UPI\"}"; expect 201 "checkout places order"
ORDER_ID=$(echo "$BODY" | jq -r .id)
[ "$(echo "$BODY" | jq -r .status)" = PAYMENT_PENDING ] && ok "order is PAYMENT_PENDING" || fail "order status" "$BODY"
call GET /cart "$TOKEN"; [ "$(echo "$BODY" | jq '.items | length')" = 0 ] && ok "cart emptied after checkout" || fail "cart not emptied" "$BODY"
has_link() { call GET "/orders/$ORDER_ID" "$TOKEN"; [ "$(echo "$BODY" | jq -r .paymentLink)" != null ]; }
wait_for "payment link arrives via Kafka (order -> payment -> order)" 30 has_link
call GET "/payment/order/$ORDER_ID/receipt" "$TOKEN"; expect 409 "no receipt before payment"
call POST "/payment/mock/$ORDER_ID/complete?success=true"; expect 200 "customer pays (mock gateway)"
is_paid() { call GET "/orders/$ORDER_ID" "$TOKEN"; [ "$(echo "$BODY" | jq -r .status)" = PAID ]; }
wait_for "order becomes PAID via payment-events" 30 is_paid
call GET "/payment/order/$ORDER_ID/receipt" "$TOKEN"; expect 200 "payment receipt"
[ "$(echo "$BODY" | jq -r .amount)" = 25499700 ] && ok "receipt amount in paise" || fail "receipt amount" "$BODY"
call GET /orders "$TOKEN"; expect 200 "order history"
[ "$(echo "$BODY" | jq -r '.content[0].id')" = "$ORDER_ID" ] && ok "latest order first" || fail "order history" "$BODY"
call PATCH "/orders/$ORDER_ID/status" "$TOKEN" '{"status":"SHIPPED"}'; expect 403 "customer cannot change status"
call PATCH "/orders/$ORDER_ID/status" "$ADMIN_TOKEN" '{"status":"DELIVERED"}'; expect 409 "cannot skip to DELIVERED"
for s in SHIPPED OUT_FOR_DELIVERY DELIVERED; do
  call PATCH "/orders/$ORDER_ID/status" "$ADMIN_TOKEN" "{\"status\":\"$s\",\"note\":\"update\"}"; expect 200 "admin moves order to $s"
done
call GET "/orders/$ORDER_ID/tracking" "$TOKEN"; expect 200 "order tracking"
[ "$(echo "$BODY" | jq -r '[.history[].status] | join(",")')" = "PAYMENT_PENDING,PAID,SHIPPED,OUT_FOR_DELIVERY,DELIVERED" ] \
  && ok "tracking shows full history" || fail "tracking history" "$BODY"

step "6. Notifications (Kafka -> Notification Service)"
notified() { BODY=$(curl -s "$NOTIFY/notifications/recent?to=$CUSTOMER_EMAIL"); echo "$BODY" | jq -e --arg s "$1" '[.[].subject] | any(contains($s))' >/dev/null; }
for s in "Welcome" "Order #$ORDER_ID confirmed" "Complete payment" "Payment receipt" "is now delivered"; do
  wait_for "email: '$s'" 20 notified "$s"
done

step "7. Password reset and logout (PRD 1.4, 6.2)"
call POST /auth/forgot-password "" "{\"email\":\"$CUSTOMER_EMAIL\"}"; expect 202 "request reset link"
reset_mail() { notified "Reset your"; }
wait_for "reset email sent" 20 reset_mail
RESET_TOKEN=$(echo "$BODY" | jq -r '[.[] | select(.subject | contains("Reset"))][0].body' | sed -E 's/.*token=([a-f0-9-]+).*/\1/')
call POST /auth/reset-password "" "{\"token\":\"$RESET_TOKEN\",\"newPassword\":\"NewSecret@456\"}"; expect 200 "reset password"
revoked() { call GET /cart "$1"; [ "$STATUS" = 401 ]; }
wait_for "old session revoked after reset (gateway cache evicted via Kafka)" 10 revoked "$TOKEN"
TOKEN=$(login "$CUSTOMER_EMAIL" "NewSecret@456"); [ -n "$TOKEN" ] && ok "login with new password" || fail "login after reset" "$BODY"
call GET /cart "$TOKEN"; expect 200 "new token works"
call POST /auth/logout "$TOKEN" "{\"token\":\"$TOKEN\"}"; expect 204 "logout"
wait_for "token rejected after logout" 10 revoked "$TOKEN"

printf '\n\033[32mAll %d checks passed.\033[0m\n' "$PASS"
