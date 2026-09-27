#!/usr/bin/env bash
# End-to-end smoke test of the running platform (start it with scripts/run-local.sh).
# Every public call goes through the API gateway on :8080, exactly like the frontend will.
# Only the test-data seeding talks to internal services directly (as an admin tool would).
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
PRODUCT_ADMIN="${PRODUCT_ADMIN:-http://localhost:8083}"
INVENTORY_ADMIN="${INVENTORY_ADMIN:-http://localhost:8084}"
RUN="$(date +%s)"            # makes every run independent (unique SKU/email/keys)
SKU="SMOKE-$RUN"
FAILURES=0

pass() { printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; FAILURES=$((FAILURES + 1)); }
expect() { # description expected actual
  if [[ "$2" == "$3" ]]; then pass "$1 ($3)"; else fail "$1: expected $2, got $3"; fi
}

# call METHOD URL [BODY] [extra curl args...]  -> sets $STATUS and $BODY
call() {
  local method=$1 url=$2 body=${3:-}
  shift 3 || shift $#
  local out
  out=$(curl -s -w $'\n%{http_code}' -X "$method" "$url" -H 'Content-Type: application/json' \
        ${body:+-d "$body"} "$@")
  STATUS="${out##*$'\n'}"
  BODY="${out%$'\n'*}"
}

echo "Seeding catalog and stock (internal admin APIs)"
call POST "$PRODUCT_ADMIN/api/v1/products" "{\"id\":\"$SKU\",\"name\":\"Smoke Keyboard\",\"price\":49.90,\"currency\":\"USD\",\"active\":true}"
expect "create product" 201 "$STATUS"
call PUT "$INVENTORY_ADMIN/api/v1/inventory/$SKU" '{"available":3}'
expect "set stock to 3" 200 "$STATUS"

echo "Public API through the gateway"
call POST "$GATEWAY/api/v1/users" "{\"email\":\"smoke-$RUN@example.com\",\"firstName\":\"Smoke\",\"lastName\":\"Test\"}"
expect "register customer" 201 "$STATUS"
CUSTOMER=$(jq -r .id <<< "$BODY")

call GET "$GATEWAY/api/v1/products/$SKU"
expect "browse product" 200 "$STATUS"

checkout() { # key quantity token
  call POST "$GATEWAY/api/v1/orders" \
    "{\"customerId\":\"$CUSTOMER\",\"items\":[{\"productId\":\"$SKU\",\"quantity\":$2}],\"paymentMethodToken\":\"$3\"}" \
    -H "Idempotency-Key: $1" -H "X-Correlation-Id: smoke-$RUN-$1" -D /tmp/smoke-headers
}

echo "Checkout: happy path"
checkout "key-$RUN-a" 2 tok_visa
expect "place order" 201 "$STATUS"
expect "order confirmed" CONFIRMED "$(jq -r .status <<< "$BODY")"
expect "server-side total (2 x 49.90)" 99.80 "$(jq -r .totalAmount <<< "$BODY")"
ORDER=$(jq -r .orderId <<< "$BODY")

checkout "key-$RUN-a" 2 tok_visa
expect "retry with same Idempotency-Key is replayed" 200 "$STATUS"
expect "replay returns the same order" "$ORDER" "$(jq -r .orderId <<< "$BODY")"

checkout "key-$RUN-a" 1 tok_visa
expect "same key with a different basket" IDEMPOTENCY_KEY_REUSED "$(jq -r .code <<< "$BODY")"

call GET "$GATEWAY/api/v1/orders/$ORDER"
expect "aggregated order details" 200 "$STATUS"
expect "details include payment" COMPLETED "$(jq -r .payment.status <<< "$BODY")"

call GET "$GATEWAY/api/v1/orders?customerId=$CUSTOMER"
expect "order history" 1 "$(jq -r .totalElements <<< "$BODY")"

echo "Checkout: failures and compensation"
checkout "key-$RUN-b" 1 tok_decline
expect "declined payment" 402 "$STATUS"
call GET "$INVENTORY_ADMIN/api/v1/inventory/$SKU"
expect "stock released after decline (3 - 2 sold)" 1 "$(jq -r .available <<< "$BODY")"

checkout "key-$RUN-c" 5 tok_visa
expect "insufficient stock" INSUFFICIENT_STOCK "$(jq -r .code <<< "$BODY")"

call POST "$GATEWAY/api/v1/orders" "{\"customerId\":\"no-such-customer\",\"items\":[{\"productId\":\"$SKU\",\"quantity\":1}],\"paymentMethodToken\":\"tok_visa\"}" \
  -H "Idempotency-Key: key-$RUN-d"
expect "unknown customer" CUSTOMER_NOT_FOUND "$(jq -r .code <<< "$BODY")"

echo "Gateway allow-list: internal services are not reachable"
for path in "POST /api/v1/payments" "POST /api/v1/reservations" "PUT /api/v1/inventory/$SKU" "POST /api/v1/orders/$ORDER/cancel"; do
  call ${path% *} "$GATEWAY${path#* }" '{}'
  expect "$path blocked" 404 "$STATUS"
done

echo
if (( FAILURES == 0 )); then
  echo "All smoke tests passed. Trace one checkout end to end with:  grep smoke-$RUN-key-$RUN-a logs/*.log"
else
  echo "$FAILURES smoke test(s) failed"; exit 1
fi
