# Phase 1: Basic Spring Boot Microservices

## Goal

Stand up seven independently buildable, runnable and testable services with clean boundaries and a shared,
consistent API contract. No inter-service calls yet, and no infrastructure dependencies (no database, Docker,
or Kafka) yet.

## What was implemented

| Area | Implementation |
|---|---|
| Build | Maven multi-module reactor: root parent → `libs/common-web` + `services/` parent → 7 services. Maven Wrapper pinned to 3.9.11 |
| Runtime | Java 21, Spring Boot 3.5.16, virtual threads enabled, graceful shutdown (20s) |
| Cross-cutting (`libs/common-web`) | `ApiError` contract + `GlobalExceptionHandler`, `CorrelationIdFilter` (MDC + response header), `PageResponse`, sort whitelisting, OpenAPI defaults. Delivered as Spring Boot auto-configuration |
| Services | user, product, inventory, order, payment and notification with real domain behaviour. master as a skeleton |
| API | `/api/v1`, DTO records, Bean Validation, correct status codes, `Location` headers, pagination/filtering/sorting |
| Health | Separate liveness and readiness probes (`/actuator/health/{liveness,readiness}`) |
| Docs | springdoc OpenAPI + Swagger UI per service |
| Config | `application.yml` + `-dev`, `-test`, `-prod`. `prod` switches to ECS JSON logs and disables Swagger |
| Persistence | In-memory repositories behind interfaces, replaced by PostgreSQL in Phase 3 |

## Directory structure

```
.
├── pom.xml                         # root parent: Boot BOM, Java 21, shared plugin config
├── mvnw, mvnw.cmd, .mvn/           # Maven Wrapper (no local Maven install required)
├── libs/
│   └── common-web/                 # cross-cutting web concerns only, no domain code
│       └── src/main/java/com/platform/common/web/
│           ├── autoconfigure/      # CommonWebAutoConfiguration
│           ├── correlation/        # CorrelationId, CorrelationIdFilter
│           ├── error/              # ApiError, ApiException (+ subclasses), GlobalExceptionHandler
│           ├── openapi/            # OpenApiConfiguration
│           └── paging/             # PageResponse, SortValidator, InMemoryPageSupport (Phase 1 only)
├── services/
│   ├── pom.xml                     # parent for deployable services (shared deps + boot plugin)
│   ├── master-service/
│   ├── user-service/
│   │   └── src/main/java/com/platform/user/
│   │       ├── api/                # controllers
│   │       │   └── dto/            # request/response records
│   │       ├── domain/             # entities, value objects, state rules
│   │       ├── repository/         # persistence port + in-memory adapter
│   │       └── service/            # use cases
│   ├── product-service/            # same layout
│   ├── inventory-service/
│   ├── order-service/
│   ├── payment-service/            # + gateway/ (payment provider port)
│   └── notification-service/       # + sender/ (delivery provider port)
└── docs/
```

## Important decisions

1. **Shared library for plumbing, not for domain.** Every service must return the same error body and handle
   correlation IDs the same way. Copying that code seven times would let the copies drift. The library contains
   no domain types, so it creates no domain coupling. It's an auto-configuration, so a service gets it just by
   depending on it.
2. **Layered packages (`api` / `service` / `domain` / `repository`) instead of full hexagonal architecture.**
   These are mostly CRUD services, and ports/adapters everywhere would be ceremony. The only explicit ports are
   where an external system will plug in: `PaymentGateway` and `NotificationSender`.
3. **Business rules live in the domain objects.** `Order`/`OrderStatus` own the state machine, `Reservation` owns
   its transitions, and `StockItem` refuses negative stock. Services orchestrate. Controllers translate HTTP.
4. **Repository interfaces with in-memory adapters.** This keeps Phase 1 free of infrastructure while fixing the
   persistence seam that Phase 3 swaps for Spring Data JPA.
5. **Money is `BigDecimal` with scale 2.** Order lines copy name and unit price at purchase time, so orders don't
   change when the catalog does. That's also why the order-service never joins the product table.
6. **Idempotency where it matters.** Payments require an `Idempotency-Key`. The key is claimed atomically (insert
   PENDING) *before* the provider is called, so a retried request can't charge twice. Reservations are idempotent
   per order reference, which the Phase 7 saga relies on.
7. **Card data never enters the system.** Only `tok_...` provider tokens are accepted, the token isn't stored
   (only a SHA-256 fingerprint for key-reuse detection), `toString()` masks it, and a test asserts it never
   appears in the response.
8. **`ReentrantLock`, not `synchronized`,** guards stock mutations. On Java 21, `synchronized` pins virtual
   threads. Phase 3 replaces the lock with DB transactions plus optimistic locking so it also works across replicas.
9. **Notification API returns `202 Accepted` already.** Delivery becomes asynchronous in Phase 7 without an API change.
10. **Profiles.** `dev` is the default for local runs. `prod` must be selected explicitly and emits JSON logs.

## Commands

```bash
./mvnw clean verify                                        # build + all tests
./mvnw -pl services/order-service -am test                 # one service (+ the shared lib it needs)
./mvnw -pl services/user-service spring-boot:run           # run one service (after one `./mvnw install`)
java -jar services/user-service/target/user-service-0.1.0-SNAPSHOT.jar
SPRING_PROFILES_ACTIVE=prod java -jar services/payment-service/target/payment-service-0.1.0-SNAPSHOT.jar
```

## Verify it works

```bash
curl -i -H 'Content-Type: application/json' -H 'X-Correlation-Id: demo-req-0001' \
  localhost:8082/api/v1/users -d '{"email":"ada@example.com","firstName":"Ada","lastName":"Lovelace"}'
# → 201, Location header, X-Correlation-Id: demo-req-0001

curl -s -H 'Content-Type: application/json' localhost:8083/api/v1/products \
  -d '{"id":"P100","name":"Keyboard","price":49.90,"currency":"USD","active":true}'
curl -s -H 'Content-Type: application/json' localhost:8083/api/v1/products/price-quotes \
  -d '{"items":[{"productId":"P100","quantity":2}]}'
# → {"currency":"USD","lines":[...],"total":99.80}

curl -s -X PUT -H 'Content-Type: application/json' localhost:8084/api/v1/inventory/P100 -d '{"available":10}'
curl -s -H 'Content-Type: application/json' localhost:8084/api/v1/reservations \
  -d '{"reference":"order-1","lines":[{"productId":"P100","quantity":2}]}'
# → status RESERVED. Repeat it and you get 200 with the same id.

for i in 1 2; do curl -s -o /dev/null -w '%{http_code}\n' -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: pay-order-1-a' localhost:8086/api/v1/payments \
  -d '{"orderId":"order-1","amount":99.80,"currency":"USD","paymentMethodToken":"tok_visa"}'; done
# → 201 then 200 (replayed, provider not called again)
```

## Tests (72, all green)

| Level | Examples |
|---|---|
| Unit (JUnit 5 + Mockito) | `UserServiceTest`, `PriceQuoteServiceTest`, `PaymentServiceTest` (idempotency incl. lost insert race), `NotificationServiceTest` |
| Domain | `OrderTest` (parameterised transition table), `ReservationTest`, `NotificationTemplateTest` |
| Web slice (`@WebMvcTest`) | `UserControllerTest`, `ProductControllerTest`, `OrderControllerTest` (page-size cap), `GlobalExceptionHandlerTest` (error contract, no leakage) |
| Service over real HTTP (`@SpringBootTest`, random port) | `*ApiTest` in every service: status codes, Location, replay semantics, probes, OpenAPI |
| Concurrency | `InventoryServiceTest.neverOversellsUnderConcurrency`: 50 virtual threads competing for 10 units |

## Known Phase 1 limitations (addressed later)

* Data is lost on restart (Phase 3: PostgreSQL + Flyway).
* Services don't call each other, and there's no gateway (Phase 2).
* The user-email uniqueness check is check-then-insert (Phase 3: unique index).
* No authentication (Phase 9). Internal endpoints such as `POST /orders` will be network-restricted and require
  service tokens.

## Next phase

**Phase 2: master/orchestrator communication.** Add the API Gateway and implement `POST /api/v1/orders` in the
master-service: validate the customer → price the basket → reserve stock → create the order → take payment →
confirm, or compensate on failure. This uses typed `RestClient`s with timeouts, correlation-ID propagation,
`Idempotency-Key` handling, and aggregated partial-failure responses.
