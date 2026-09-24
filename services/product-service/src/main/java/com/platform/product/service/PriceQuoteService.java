package com.platform.product.service;

import com.platform.common.web.error.BusinessRuleException;
import com.platform.product.api.dto.PriceQuoteRequest;
import com.platform.product.api.dto.PriceQuoteResponse;
import com.platform.product.domain.Money;
import com.platform.product.domain.Product;
import com.platform.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Prices a basket against the current catalog. Pricing rules live here and nowhere else. */
@Service
public class PriceQuoteService {

    private final ProductRepository repository;

    public PriceQuoteService(ProductRepository repository) {
        this.repository = repository;
    }

    public PriceQuoteResponse quote(PriceQuoteRequest request) {
        // Merge duplicate lines, preserving the client's ordering.
        Map<String, Integer> quantities = request.items().stream().collect(Collectors.toMap(
                PriceQuoteRequest.Item::productId, PriceQuoteRequest.Item::quantity, Integer::sum, LinkedHashMap::new));

        Map<String, Product> products = repository.findAllById(quantities.keySet()).stream()
                .filter(Product::isActive)
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<String> unavailable = quantities.keySet().stream().filter(id -> !products.containsKey(id)).toList();
        if (!unavailable.isEmpty()) {
            throw new BusinessRuleException("PRODUCT_UNAVAILABLE", "Products not available for sale: " + unavailable);
        }

        List<String> currencies = products.values().stream().map(p -> p.getPrice().currency()).distinct().toList();
        if (currencies.size() > 1) {
            throw new BusinessRuleException("MIXED_CURRENCIES", "A quote cannot mix currencies: " + currencies);
        }

        List<PriceQuoteResponse.Line> lines = quantities.entrySet().stream().map(entry -> {
            Product product = products.get(entry.getKey());
            Money lineTotal = product.getPrice().times(entry.getValue());
            return new PriceQuoteResponse.Line(product.getId(), product.getName(), entry.getValue(),
                    product.getPrice().amount(), lineTotal.amount());
        }).toList();
        BigDecimal total = lines.stream().map(PriceQuoteResponse.Line::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PriceQuoteResponse(currencies.getFirst(), lines, total);
    }
}
