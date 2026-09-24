package com.platform.inventory.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param reference caller's business key (normally the order ID). Makes the call idempotent: retrying
 *                  with the same reference returns the existing reservation instead of reserving twice.
 */
public record CreateReservationRequest(
        @NotBlank @Size(max = 64) String reference,
        @NotEmpty @Size(max = 50) List<@Valid Line> lines) {

    public record Line(@NotBlank String productId, @Min(1) @Max(1000) int quantity) {
    }
}
