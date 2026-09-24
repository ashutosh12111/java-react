package com.platform.inventory.repository;

import com.platform.common.web.paging.InMemoryPageSupport;
import com.platform.inventory.domain.StockItem;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class InMemoryStockRepository implements StockRepository {

    private static final Map<String, Comparator<StockItem>> SORTABLE = Map.of(
            "productId", Comparator.comparing(StockItem::getProductId),
            "available", Comparator.comparingInt(StockItem::getAvailable),
            "updatedAt", Comparator.comparing(StockItem::getUpdatedAt));

    private final Map<String, StockItem> stock = new ConcurrentHashMap<>();

    @Override
    public StockItem save(StockItem item) {
        stock.put(item.getProductId(), item);
        return item;
    }

    @Override
    public Optional<StockItem> findByProductId(String productId) {
        return Optional.ofNullable(stock.get(productId));
    }

    @Override
    public Page<StockItem> search(Integer maxAvailable, Pageable pageable) {
        return InMemoryPageSupport.page(
                stock.values().stream().filter(s -> maxAvailable == null || s.getAvailable() <= maxAvailable),
                pageable, SORTABLE, Comparator.comparing(StockItem::getProductId));
    }
}
