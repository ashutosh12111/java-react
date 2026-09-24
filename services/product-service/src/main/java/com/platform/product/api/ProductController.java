package com.platform.product.api;

import com.platform.common.web.paging.PageResponse;
import com.platform.product.api.dto.CreateProductRequest;
import com.platform.product.api.dto.PriceQuoteRequest;
import com.platform.product.api.dto.PriceQuoteResponse;
import com.platform.product.api.dto.ProductRequest;
import com.platform.product.api.dto.ProductResponse;
import com.platform.product.domain.Product;
import com.platform.product.service.PriceQuoteService;
import com.platform.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products")
public class ProductController {

    private final ProductService productService;
    private final PriceQuoteService priceQuoteService;

    public ProductController(ProductService productService, PriceQuoteService priceQuoteService) {
        this.productService = productService;
        this.priceQuoteService = priceQuoteService;
    }

    @PostMapping
    @Operation(summary = "Add a product to the catalog")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        Product product = productService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(product.getId()).toUri();
        return ResponseEntity.created(location).body(ProductResponse.from(product));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by SKU")
    public ProductResponse get(@PathVariable String id) {
        return ProductResponse.from(productService.get(id));
    }

    @GetMapping
    @Operation(summary = "Browse the catalog", description = "Sortable by: id, name, price, createdAt")
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(name = "q", required = false) String nameContains,
            @ParameterObject @PageableDefault(sort = "name") Pageable pageable) {
        return PageResponse.from(productService.search(active, nameContains, pageable), ProductResponse::from);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a product's attributes")
    public ProductResponse update(@PathVariable String id, @Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a product from the catalog")
    public void delete(@PathVariable String id) {
        productService.delete(id);
    }

    @PostMapping("/price-quotes")
    @Operation(summary = "Price a basket", description = "Returns authoritative unit prices and totals. Nothing is persisted.")
    public PriceQuoteResponse quote(@Valid @RequestBody PriceQuoteRequest request) {
        return priceQuoteService.quote(request);
    }
}
