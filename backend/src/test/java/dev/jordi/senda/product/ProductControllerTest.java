package dev.jordi.senda.product;

import dev.jordi.senda.apitoken.ApiTokenService;
import dev.jordi.senda.common.GlobalExceptionHandler;
import dev.jordi.senda.common.JwtAuthFilter;
import dev.jordi.senda.common.JwtService;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
class ProductControllerTest {

    private static final Long USER_ID = 1L;

    @MockitoBean private ApiTokenService apiTokenService;
    @MockitoBean private ProductService productService;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtService.generateToken(USER_ID);
    }

    private static final ProductResponse SAMPLE = new ProductResponse(
            1L, "Leche", UnitType.WEIGHT, new BigDecimal("1.000"), "L",
            Instant.parse("2026-07-11T10:00:00Z"),
            List.of(new CurrentPriceResponse("Lidl", new BigDecimal("1.10"),
                    Instant.parse("2026-07-11T10:00:00Z"))));

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.unitType").exists())
                .andExpect(jsonPath("$.fieldErrors.amount").exists())
                .andExpect(jsonPath("$.fieldErrors.unit").exists());
    }

    @Test
    void postValidReturns201() throws Exception {
        when(productService.create(eq(USER_ID), any(ProductRequest.class))).thenReturn(SAMPLE);
        mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Leche","unitType":"WEIGHT","amount":1.0,"unit":"L"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.currentPrices[0].supermarket").value("Lidl"));
    }

    @Test
    void listReturnsProducts() throws Exception {
        when(productService.list(USER_ID)).thenReturn(List.of(SAMPLE));
        mockMvc.perform(get("/api/products").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        verify(productService).list(USER_ID);
    }

    @Test
    void postPriceValidReturns201() throws Exception {
        PriceEntryResponse pe = new PriceEntryResponse(
                55L, new BigDecimal("1.15"), "Lidl", Instant.parse("2026-07-11T10:00:00Z"));
        when(productService.addPrice(eq(USER_ID), eq(1L), any(PriceEntryRequest.class))).thenReturn(pe);
        mockMvc.perform(post("/api/products/1/prices")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"price":1.15,"supermarket":"Lidl"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.supermarket").value("Lidl"));
    }

    @Test
    void postPriceOnForeignProductReturns404() throws Exception {
        when(productService.addPrice(eq(USER_ID), eq(99L), any(PriceEntryRequest.class)))
                .thenThrow(new NotFoundException("Product not found"));
        mockMvc.perform(post("/api/products/99/prices")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"price":1.15,"supermarket":"Lidl"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getHistoryReturnsList() throws Exception {
        when(productService.priceHistory(USER_ID, 1L)).thenReturn(List.of(
                new PriceEntryResponse(55L, new BigDecimal("1.15"), "Lidl",
                        Instant.parse("2026-07-11T10:00:00Z"))));
        mockMvc.perform(get("/api/products/1/prices").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/products/1").header("Authorization", bearer))
                .andExpect(status().isNoContent());
        verify(productService).delete(USER_ID, 1L);
    }
}
