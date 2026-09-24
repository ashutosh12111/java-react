package com.platform.product.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.product.service.PriceQuoteService;
import com.platform.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private PriceQuoteService priceQuoteService;

    @Test
    void rejectsInvalidProduct() throws Exception {
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("""
                        {"id":"p 100","name":"Widget","price":-1,"currency":"usd","active":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
        verifyNoInteractions(productService);
    }

    @Test
    void rejectsInvalidQuoteItems() throws Exception {
        mvc.perform(post("/api/v1/products/price-quotes").contentType(MediaType.APPLICATION_JSON).content("""
                        {"items":[{"productId":"P100","quantity":0}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"));
        verifyNoInteractions(priceQuoteService);
    }
}
