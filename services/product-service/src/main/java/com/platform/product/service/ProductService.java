package com.platform.product.service;

import com.platform.common.web.error.ConflictException;
import com.platform.common.web.error.ResourceNotFoundException;
import com.platform.product.api.dto.CreateProductRequest;
import com.platform.product.api.dto.ProductRequest;
import com.platform.product.domain.Money;
import com.platform.product.domain.Product;
import com.platform.product.repository.ProductRepository;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final ProductRepository repository;
    private final Clock clock;

    public ProductService(ProductRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Product create(CreateProductRequest request) {
        if (repository.existsById(request.id())) {
            throw new ConflictException("PRODUCT_ALREADY_EXISTS", "Product '" + request.id() + "' already exists");
        }
        return repository.save(new Product(request.id(), request.name(), request.description(),
                new Money(request.price(), request.currency()), request.active(), clock.instant()));
    }

    public Product get(String id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    public Page<Product> search(Boolean active, String nameContains, Pageable pageable) {
        return repository.search(active, nameContains, pageable);
    }

    public Product update(String id, ProductRequest request) {
        Product product = get(id);
        product.update(request.name(), request.description(), new Money(request.price(), request.currency()),
                request.active(), clock.instant());
        return repository.save(product);
    }

    public void delete(String id) {
        if (!repository.deleteById(id)) {
            throw new ResourceNotFoundException("Product", id);
        }
    }
}
