package com.platform.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.platform.common.web.error.BusinessRuleException;
import com.platform.product.api.dto.PriceQuoteRequest;
import com.platform.product.api.dto.PriceQuoteResponse;
import com.platform.product.domain.Money;
import com.platform.product.domain.Product;
import com.platform.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceQuoteServiceTest {

    @Mock
    private ProductRepository repository;

    @InjectMocks
    private PriceQuoteService service;

    private static Product product(String id, String price, String currency, boolean active) {
        return new Product(id, "Product " + id, null, new Money(new BigDecimal(price), currency), active, Instant.EPOCH);
    }

    private static PriceQuoteRequest.Item item(String id, int qty) {
        return new PriceQuoteRequest.Item(id, qty);
    }

    @Test
    void pricesLinesAndMergesDuplicates() {
        given(repository.findAllById(any())).willReturn(List.of(
                product("P100", "19.99", "USD", true), product("P200", "5.00", "USD", true)));

        PriceQuoteResponse quote = service.quote(new PriceQuoteRequest(List.of(item("P100", 2), item("P200", 1), item("P100", 1))));

        assertThat(quote.lines()).hasSize(2);
        assertThat(quote.lines().getFirst().quantity()).isEqualTo(3);
        assertThat(quote.lines().getFirst().lineTotal()).isEqualByComparingTo("59.97");
        assertThat(quote.total()).isEqualByComparingTo("64.97");
        assertThat(quote.currency()).isEqualTo("USD");
    }

    @Test
    void rejectsUnknownAndInactiveProducts() {
        given(repository.findAllById(any())).willReturn(List.of(product("P100", "1.00", "USD", false)));

        assertThatThrownBy(() -> service.quote(new PriceQuoteRequest(List.of(item("P100", 1), item("NOPE", 1)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("P100").hasMessageContaining("NOPE");
    }

    @Test
    void rejectsMixedCurrencies() {
        given(repository.findAllById(any())).willReturn(List.of(
                product("P100", "1.00", "USD", true), product("P200", "1.00", "EUR", true)));

        assertThatThrownBy(() -> service.quote(new PriceQuoteRequest(List.of(item("P100", 1), item("P200", 1)))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("MIXED_CURRENCIES");
    }
}
