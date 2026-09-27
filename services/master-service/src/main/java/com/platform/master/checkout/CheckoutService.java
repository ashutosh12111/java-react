package com.platform.master.checkout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.web.error.ApiException;
import com.platform.common.web.error.BusinessRuleException;
import com.platform.common.web.error.ConflictException;
import com.platform.master.checkout.CheckoutIdempotencyStore.Entry;
import com.platform.master.checkout.CheckoutIdempotencyStore.StoredOutcome;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Idempotency at the edge of the platform for {@code POST /api/v1/orders}.
 *
 * <ul>
 *   <li>Same key + same body, finished → the stored outcome is replayed (no downstream calls).</li>
 *   <li>Same key + same body, still running → 409 CHECKOUT_IN_PROGRESS.</li>
 *   <li>Same key + different body → 422 IDEMPOTENCY_KEY_REUSED.</li>
 *   <li>Outcomes with 4xx status are final and stored. 5xx outcomes are forgotten, so the client can
 *       retry with the same key and the checkout resumes.</li>
 * </ul>
 */
@Service
public class CheckoutService {

    private final OrderPlacementService placement;
    private final CheckoutIdempotencyStore store;
    private final ObjectMapper objectMapper;

    public CheckoutService(OrderPlacementService placement, CheckoutIdempotencyStore store, ObjectMapper objectMapper) {
        this.placement = placement;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public CheckoutResult checkout(String idempotencyKey, PlaceOrderRequest request) {
        String checkoutId = checkoutId(request.customerId(), idempotencyKey);
        String fingerprint = fingerprint(request);

        Optional<Entry> existing = store.claim(checkoutId, fingerprint);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }
        try {
            PlaceOrderResponse response = placement.place(checkoutId, request);
            store.complete(checkoutId, new StoredOutcome.Success(response));
            return new CheckoutResult(response, false);
        } catch (ApiException e) {
            if (e.status().is4xxClientError()) {
                store.complete(checkoutId, new StoredOutcome.Failure(e.status().value(), e.code(), e.getMessage()));
            } else {
                store.release(checkoutId);
            }
            throw e;
        } catch (RuntimeException e) {
            store.release(checkoutId);
            throw e;
        }
    }

    private static CheckoutResult replay(Entry entry, String fingerprint) {
        if (!entry.fingerprint().equals(fingerprint)) {
            throw new BusinessRuleException("IDEMPOTENCY_KEY_REUSED",
                    "The Idempotency-Key was already used for a different checkout");
        }
        return switch (entry.outcome()) {
            case null -> throw new ConflictException("CHECKOUT_IN_PROGRESS",
                    "A checkout with this Idempotency-Key is still being processed");
            case StoredOutcome.Success success -> new CheckoutResult(success.response(), true);
            case StoredOutcome.Failure failure ->
                    throw new ReplayedFailureException(failure.status(), failure.code(), failure.message());
        };
    }

    /**
     * Keys are chosen by clients, so they are scoped per customer: two customers using the same key never
     * collide. The derived ID is deterministic, so every retry drives the SAME downstream references.
     */
    static String checkoutId(String customerId, String idempotencyKey) {
        return UUID.nameUUIDFromBytes((customerId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String fingerprint(PlaceOrderRequest request) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(request));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot fingerprint request", e);
        }
    }

    public record CheckoutResult(PlaceOrderResponse response, boolean replayed) {
    }
}
