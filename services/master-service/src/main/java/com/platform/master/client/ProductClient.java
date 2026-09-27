package com.platform.master.client;

import com.platform.master.config.DownstreamProperties;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ProductClient {

    static final String SERVICE = "product-service";
    private final RestClient http;

    public ProductClient(DownstreamRestClientFactory factory, DownstreamProperties properties) {
        this.http = factory.create(SERVICE, properties.product());
    }

    public PriceQuote quote(List<QuoteItem> items) {
        return DownstreamRestClientFactory.call(SERVICE, () -> http.post()
                .uri("/api/v1/products/price-quotes")
                .body(new QuoteRequest(items))
                .retrieve()
                .body(PriceQuote.class));
    }

    public record QuoteItem(String productId, int quantity) {
    }

    record QuoteRequest(List<QuoteItem> items) {
    }

    public record PriceQuote(String currency, List<Line> lines, BigDecimal total) {
        public record Line(String productId, String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        }
    }
}
