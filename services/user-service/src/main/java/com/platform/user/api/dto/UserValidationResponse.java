package com.platform.user.api.dto;

import com.platform.user.domain.UserStatus;
import java.util.UUID;

/**
 * Lightweight answer to "may this customer place an order?". The master-service calls this instead of
 * fetching the full profile, so it never needs to know the user-service's eligibility rules.
 */
public record UserValidationResponse(UUID userId, UserStatus status, boolean eligibleForOrders) {
}
