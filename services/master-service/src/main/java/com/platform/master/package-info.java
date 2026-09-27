/**
 * Master / orchestrator service.
 *
 * <p>Responsibilities: coordinate multi-service workflows (e.g. placing an order), aggregate responses,
 * handle partial failures, apply timeout/retry/circuit-breaker policies, propagate correlation and
 * trace context, and enforce idempotency at the edge of the platform.
 *
 * <p>Non-responsibilities: it owns NO database and NO domain rules. Pricing lives in product-service,
 * stock rules in inventory-service, order state in order-service, and so on. If logic here starts
 * answering a domain question, it belongs in the owning service instead.
 *
 * <p>Packages: {@code client} (one typed client per downstream service), {@code checkout} (the order
 * placement workflow and its idempotency), {@code query} (read-side aggregation), {@code api} (HTTP).
 */
package com.platform.master;
