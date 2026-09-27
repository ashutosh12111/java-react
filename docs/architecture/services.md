# Service Catalog and Endpoints

All paths are versioned under `/api/v1`. Each running service serves its full, interactive contract at
`http://localhost:<port>/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`). Swagger is disabled in the `prod` profile.

| Service | Port | Swagger |
|---|---|---|
| **api-gateway** (public) | 8080 | none: routes to the services below |
| master-service | 8081 | http://localhost:8081/swagger-ui.html |
| user-service | 8082 | http://localhost:8082/swagger-ui.html |
| product-service | 8083 | http://localhost:8083/swagger-ui.html |
| inventory-service | 8084 | http://localhost:8084/swagger-ui.html |
| order-service | 8085 | http://localhost:8085/swagger-ui.html |
| payment-service | 8086 | http://localhost:8086/swagger-ui.html |
| notification-service | 8087 | http://localhost:8087/swagger-ui.html |

Every service also exposes `/actuator/health/liveness`, `/actuator/health/readiness` and `/actuator/info`.

## user-service

| Method | Path | Purpose |
|---|---|---|
| POST | `/users` | Register (409 `USER_EMAIL_ALREADY_EXISTS`) |
| GET | `/users/{id}` | Get |
| GET | `/users?status=&email=&page=&size=&sort=` | List. Sort: `createdAt`, `email`, `lastName` |
| PUT | `/users/{id}` | Replace profile (email is immutable) |
| PATCH | `/users/{id}/status` | `ACTIVE` / `SUSPENDED` |
| DELETE | `/users/{id}` | Delete |
| GET | `/users/{id}/validation` | `{userId, status, eligibleForOrders, email}` for orchestration |

## product-service

| Method | Path | Purpose |
|---|---|---|
| POST | `/products` | Add (ID is the SKU, e.g. `P100`) |
| GET | `/products/{id}` | Get |
| GET | `/products?active=&q=&page=&size=&sort=` | Browse. Sort: `id`, `name`, `price`, `createdAt` |
| PUT | `/products/{id}` | Replace attributes |
| DELETE | `/products/{id}` | Remove |
| POST | `/products/price-quotes` | Price a basket. 422 `PRODUCT_UNAVAILABLE`, `MIXED_CURRENCIES` |

## inventory-service

| Method | Path | Purpose |
|---|---|---|
| PUT | `/inventory/{productId}` | Set available stock (idempotent upsert) |
| GET | `/inventory/{productId}` | Available and reserved quantities |
| GET | `/inventory?maxAvailable=` | List / low-stock report. Sort: `productId`, `available`, `updatedAt` |
| POST | `/reservations` | Reserve all-or-nothing. 201 new, 200 replay, 409 reference conflict, 422 `INSUFFICIENT_STOCK` |
| GET | `/reservations/{id}` | Get |
| POST | `/reservations/{id}/release` | Compensation: return stock (idempotent) |
| POST | `/reservations/{id}/commit` | Sale completed (idempotent) |

Reservation states: `RESERVED → RELEASED` or `RESERVED → COMMITTED`. Both targets are terminal.

## order-service

| Method | Path | Purpose |
|---|---|---|
| POST | `/orders` | Create `PENDING` order (internal: prices come from the product-service quote). Idempotent per `reference`: 201 new, 200 replay, 409 `ORDER_REFERENCE_CONFLICT` |
| GET | `/orders/{id}` | Get, including status history |
| GET | `/orders?customerId=&status=` | Order history. Sort: `createdAt`, `totalAmount`, `status` |
| POST | `/orders/{id}/confirm` | `PENDING → CONFIRMED` |
| POST | `/orders/{id}/cancel` | `PENDING → CANCELLED` (reason required) |

Order states: `PENDING → CONFIRMED | CANCELLED`. Invalid transitions return 409 `INVALID_ORDER_STATE`.

## payment-service

| Method | Path | Purpose |
|---|---|---|
| POST | `/payments` | Pay. Requires `Idempotency-Key`. 201 new (see `status`), 200 replay |
| GET | `/payments/{id}` | Status |
| GET | `/payments?orderId=&status=` | List. Sort: `createdAt`, `amount` |

Only provider tokens (`tok_...`) are accepted, never card numbers. Test tokens: `tok_visa` (approved),
`tok_decline` (card_declined), `tok_insufficient_funds`.

## notification-service

| Method | Path | Purpose |
|---|---|---|
| POST | `/notifications` | Request delivery → `202 Accepted` |
| GET | `/notifications/{id}` | Delivery status (recipient masked) |
| GET | `/notifications?status=&channel=&reference=` | List. Sort: `createdAt` |

Templates: `WELCOME`, `ORDER_CONFIRMED`, `ORDER_CANCELLED`, `PAYMENT_FAILED`. Recipients ending in `.invalid`
simulate a provider rejection.

## api-gateway: public routes

Only these reach the platform from outside. Everything else returns 404 `NOT_FOUND`.

| Method | Path | Routed to |
|---|---|---|
| POST, GET | `/api/v1/orders`, `/api/v1/orders/{id}` | master-service |
| GET | `/api/v1/products`, `/api/v1/products/{id}` | product-service |
| POST | `/api/v1/users` | user-service |
| GET | `/api/v1/users/{id}` | user-service |

Gateway-level errors use the platform error format: `404 NOT_FOUND`, `503 SERVICE_UNAVAILABLE` (upstream down),
`504 GATEWAY_TIMEOUT` (upstream too slow). Dev profile only: `GET /actuator/gateway/routes` lists the active routes.

## master-service (public, via the gateway)

| Method | Path | Purpose |
|---|---|---|
| POST | `/orders` | Checkout. Requires `Idempotency-Key`. Body: `customerId`, `items[{productId, quantity}]`, `paymentMethodToken` (no prices) |
| GET | `/orders/{id}` | Order and payment status, aggregated in parallel. `warnings` lists any part that couldn't be loaded |
| GET | `/orders?customerId=&status=&page=&size=&sort=` | Order history (`customerId` required). Sort: `createdAt`, `totalAmount`, `status` |

Checkout outcomes: `201` placed, `200` replay (`Idempotent-Replayed: true`), `402 PAYMENT_DECLINED`,
`409 CHECKOUT_IN_PROGRESS` / `CHECKOUT_ALREADY_CANCELLED`, `422 CUSTOMER_NOT_FOUND` / `CUSTOMER_NOT_ELIGIBLE` /
`PRODUCT_UNAVAILABLE` / `INSUFFICIENT_STOCK` / `IDEMPOTENCY_KEY_REUSED`, `503 DOWNSTREAM_UNAVAILABLE` /
`PAYMENT_OUTCOME_UNKNOWN` (retry with the same key). Details are in [Phase 2](../phases/phase-02-orchestration.md).
