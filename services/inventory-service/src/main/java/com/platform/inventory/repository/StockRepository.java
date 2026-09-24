package com.platform.inventory.repository;

import com.platform.inventory.domain.StockItem;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StockRepository {

    StockItem save(StockItem item);

    Optional<StockItem> findByProductId(String productId);

    /** @param maxAvailable optional low-stock filter */
    Page<StockItem> search(Integer maxAvailable, Pageable pageable);
}
