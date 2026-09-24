package com.platform.inventory.api;

import com.platform.common.web.paging.PageResponse;
import com.platform.inventory.api.dto.SetStockRequest;
import com.platform.inventory.api.dto.StockResponse;
import com.platform.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Stock")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Set the available stock for a product (idempotent upsert)")
    public StockResponse setStock(@PathVariable String productId, @Valid @RequestBody SetStockRequest request) {
        return StockResponse.from(inventoryService.setStock(productId, request.available()));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get stock for a product")
    public StockResponse get(@PathVariable String productId) {
        return StockResponse.from(inventoryService.getStock(productId));
    }

    @GetMapping
    @Operation(summary = "List stock levels", description = "Use maxAvailable to find low-stock items. Sortable by: productId, available, updatedAt")
    public PageResponse<StockResponse> list(
            @RequestParam(required = false) Integer maxAvailable,
            @ParameterObject @PageableDefault(sort = "productId") Pageable pageable) {
        return PageResponse.from(inventoryService.searchStock(maxAvailable, pageable), StockResponse::from);
    }
}
