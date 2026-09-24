# Service Catalog and Endpoints

All paths are versioned under `/api/v1`. Each running service serves its full, interactive contract at
`http://localhost:<port>/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`). Swagger is disabled in the `prod` profile.

| Service | Port | Swagger |
|---|---|---|
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
| GET | `/users/{id}/validation` | `{userId, status, eligibleForOrders}` for orchestration |

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
| POST | `/orders` | Create `PENDING` order (internal: prices come from the product-service quote) |
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

## master-service

Phase 1 ships a skeleton only (health, OpenAPI, error contract, correlation). Phase 2 adds `POST /api/v1/orders`
orchestration.
