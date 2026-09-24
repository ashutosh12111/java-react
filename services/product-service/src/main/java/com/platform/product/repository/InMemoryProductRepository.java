package com.platform.product.repository;

import com.platform.common.web.paging.InMemoryPageSupport;
import com.platform.product.domain.Product;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class InMemoryProductRepository implements ProductRepository {

    private static final Map<String, Comparator<Product>> SORTABLE = Map.of(
            "id", Comparator.comparing(Product::getId),
            "name", Comparator.comparing(Product::getName),
            "price", Comparator.comparing(Product::getPriceAmount),
            "createdAt", Comparator.comparing(Product::getCreatedAt));

    private final Map<String, Product> products = new ConcurrentHashMap<>();

    @Override
    public Product save(Product product) {
        products.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(products.get(id));
    }

    @Override
    public List<Product> findAllById(Collection<String> ids) {
        return ids.stream().map(products::get).filter(Objects::nonNull).toList();
    }

    @Override
    public boolean existsById(String id) {
        return products.containsKey(id);
    }

    @Override
    public Page<Product> search(Boolean active, String nameContains, Pageable pageable) {
        String needle = nameContains == null ? null : nameContains.toLowerCase(Locale.ROOT);
        return InMemoryPageSupport.page(
                products.values().stream()
                        .filter(p -> active == null || p.isActive() == active)
                        .filter(p -> needle == null || p.getName().toLowerCase(Locale.ROOT).contains(needle)),
                pageable, SORTABLE, Comparator.comparing(Product::getId));
    }

    @Override
    public boolean deleteById(String id) {
        return products.remove(id) != null;
    }
}
