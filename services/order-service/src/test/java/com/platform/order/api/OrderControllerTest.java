package com.platform.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.common.web.error.ConflictException;
import com.platform.order.service.OrderService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void invalidTransitionIs409() throws Exception {
        UUID id = UUID.randomUUID();
        given(orderService.cancel(eq(id), any()))
                .willThrow(new ConflictException("INVALID_ORDER_STATE", "Order is CONFIRMED and cannot become CANCELLED"));

        mvc.perform(post("/api/v1/orders/{id}/cancel", id).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
    }

    @Test
    void listAppliesDefaultsAndCapsPageSize() throws Exception {
        PageRequest expected = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt"));
        given(orderService.search("c1", null, expected)).willReturn(Page.empty(expected));

        mvc.perform(get("/api/v1/orders").param("customerId", "c1").param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void rejectsNegativePrices() throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"reference":"checkout-1","customerId":"c1","currency":"USD",
                         "lines":[{"productId":"P100","productName":"K","quantity":1,"unitPrice":-5}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].unitPrice"));
    }
}
