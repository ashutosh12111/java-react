package com.platform.inventory.api;

import com.platform.inventory.api.dto.CreateReservationRequest;
import com.platform.inventory.api.dto.ReservationResponse;
import com.platform.inventory.service.InventoryService;
import com.platform.inventory.service.ReservationOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations")
public class ReservationController {

    private final InventoryService inventoryService;

    public ReservationController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    @Operation(summary = "Reserve stock (all-or-nothing)",
            description = "Idempotent per reference: 201 when created, 200 when an identical reservation already exists, "
                    + "409 if the reference was used for different lines, 422 if stock is insufficient.")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody CreateReservationRequest request) {
        ReservationOutcome outcome = inventoryService.reserve(request);
        ReservationResponse body = ReservationResponse.from(outcome.reservation());
        if (!outcome.created()) {
            return ResponseEntity.ok(body);
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a reservation")
    public ReservationResponse get(@PathVariable UUID id) {
        return ReservationResponse.from(inventoryService.getReservation(id));
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "Release reserved stock (compensation)", description = "Idempotent. 409 if already committed.")
    public ReservationResponse release(@PathVariable UUID id) {
        return ReservationResponse.from(inventoryService.release(id));
    }

    @PostMapping("/{id}/commit")
    @Operation(summary = "Commit reserved stock (sale completed)", description = "Idempotent. 409 if already released.")
    public ReservationResponse commit(@PathVariable UUID id) {
        return ReservationResponse.from(inventoryService.commit(id));
    }
}
