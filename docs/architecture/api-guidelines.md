# API Guidelines

Every service follows these conventions. Most of them are implemented once in `libs/common-web`.

## Versioning

URI versioning: `/api/v1/...`. Breaking changes get a new major version (`/api/v2`) served side by side
until consumers migrate. Additive changes (new optional fields, new endpoints) don't bump the version, so clients
must ignore unknown JSON fields.

## Methods and status codes

| Operation | Method | Success | Typical errors |
|---|---|---|---|
| Create | `POST /resources` | `201 Created` + `Location` | 400, 409, 422 |
| Create, async processing | `POST /resources` | `202 Accepted` + `Location` | 400 |
| Idempotent replay of a create | `POST` (same key/reference) | `200 OK` | 409 (in progress), 422 (key reused) |
| Read one | `GET /resources/{id}` | `200 OK` | 404 |
| List | `GET /resources?page=&size=&sort=` | `200 OK` | 400 (bad sort/filter) |
| Replace | `PUT /resources/{id}` | `200 OK` | 400, 404 |
| Partial state change | `PATCH /resources/{id}/status` | `200 OK` | 400, 404 |
| Domain command | `POST /resources/{id}/confirm` | `200 OK` | 404, 409 (invalid state) |
| Delete | `DELETE /resources/{id}` | `204 No Content` | 404 |

`400` means the request is malformed. `409` means it conflicts with the current state (duplicate key, invalid
transition). `422` means it's well-formed but breaks a business rule (insufficient stock, mixed currencies).

## Error contract

```json
{
  "timestamp": "2026-01-01T10:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Invalid request",
  "path": "/api/v1/users",
  "correlationId": "4f1c2b0e-...",
  "errors": [{ "field": "email", "message": "must be a well-formed email address" }]
}
```

* `code` is stable and machine-readable, so clients switch on it and never parse `message`.
* Rejected values are **not** echoed back (they may be card numbers, passwords, or PII).
* `500` responses never include exception messages or stack traces. The details are logged with the correlation ID.

Common codes: `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `MISSING_HEADER`, `INVALID_PARAMETER`,
`INVALID_SORT_PROPERTY`, `<RESOURCE>_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `INTERNAL_ERROR`, plus domain codes such
as `INSUFFICIENT_STOCK`, `INVALID_ORDER_STATE`, `IDEMPOTENCY_KEY_REUSED`.

## Pagination, filtering, sorting

```
GET /api/v1/orders?customerId=c-1&status=CONFIRMED&page=0&size=20&sort=createdAt,desc
```

* `page` is zero-based. `size` defaults to 20 and is capped at 100.
* `sort=property,(asc|desc)` can be repeated. Properties are **whitelisted** per endpoint (listed in Swagger), and
  anything else returns `400 INVALID_SORT_PROPERTY`. This keeps internal field names private and prevents sorts
  on unindexed columns.
* Filters are plain query parameters.

Response envelope:

```json
{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0,
  "first": true, "last": true, "sort": ["createdAt,desc"] }
```

## Idempotency

* **payment-service** requires an `Idempotency-Key` header (8–64 chars `[A-Za-z0-9_-]`). The same key with the same
  body returns the stored result (`200`, `Idempotent-Replayed: true`) without calling the provider again. The same key
  with a different body returns `422 IDEMPOTENCY_KEY_REUSED`, and a concurrent duplicate returns `409 PAYMENT_IN_PROGRESS`.
* **inventory-service** reservations and **order-service** order creation are idempotent per business `reference`.
* **master-service** requires `Idempotency-Key` on `POST /api/v1/orders`. It derives a per-customer `checkoutId` and
  uses it as the reservation reference, order reference and payment idempotency key, so a retried checkout resumes
  instead of duplicating work. See [Phase 2](../phases/phase-02-orchestration.md#idempotency-why-retries-are-safe).

## Correlation IDs

* Clients may send `X-Correlation-Id` (8–64 chars `[A-Za-z0-9._-]`). Otherwise a UUID is generated.
  Invalid values are replaced, not rejected, which also prevents log injection.
* The ID is echoed on every response, included in every error body, and written to every log line (MDC key `correlationId`).
* The API gateway assigns it for every public request. `common-web` forwards it on every outgoing `RestClient` call,
  including parallel aggregation calls (`MdcTaskDecorator`). From Phase 10 it sits alongside W3C `traceparent`.

## DTOs

Requests and responses are immutable Java `record`s, separate from domain objects. Domain objects are never
serialised directly, which keeps the API stable when internals change and means sensitive fields can't be
exposed by accident.
