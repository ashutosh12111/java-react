package com.platform.product.repository;

import com.platform.product.domain.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Persistence port; in-memory in Phase 1, Spring Data JPA over PostgreSQL from Phase 3. */
public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(String id);

    List<Product> findAllById(Collection<String> ids);

    boolean existsById(String id);

    Page<Product> search(Boolean active, String nameContains, Pageable pageable);

    boolean deleteById(String id);
}
