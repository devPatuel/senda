package dev.jordi.senda.shopping;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShoppingController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
class ShoppingControllerTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ShoppingService shoppingService;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtService.generateToken(USER_ID);
    }

    private static final ShoppingItemResponse GROCERY_SAMPLE = new ShoppingItemResponse(
            1L, ShoppingListType.GROCERY, "Leche", null, null, null, null,
            null, false, null, null, Instant.parse("2026-06-16T10:00:00Z"));

    private static final ShoppingItemResponse WISHLIST_SAMPLE = new ShoppingItemResponse(
            2L, ShoppingListType.WISHLIST, "NAS", new BigDecimal("500.00"), 10L, "Ahorro",
            new BigDecimal("800.00"), 1, false, true, "Para el servidor", Instant.parse("2026-06-16T10:00:00Z"));

    // --- 401 without token ---

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/shopping/items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // --- validation 400 ---

    @Test
    void postWithMissingRequiredFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/shopping/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.listType").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void postWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/shopping/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"listType": "GROCERY", "name": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void postWithNegativePriceReturns400() throws Exception {
        mockMvc.perform(post("/api/shopping/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"listType": "WISHLIST", "name": "NAS", "estimatedPrice": -1.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.estimatedPrice").exists());
    }

    // --- 201 create ---

    @Test
    void postValidGroceryReturns201() throws Exception {
        when(shoppingService.create(eq(USER_ID), any(ShoppingItemRequest.class))).thenReturn(GROCERY_SAMPLE);

        mockMvc.perform(post("/api/shopping/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"listType": "GROCERY", "name": "Leche"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.listType").value("GROCERY"))
                .andExpect(jsonPath("$.name").value("Leche"))
                .andExpect(jsonPath("$.bought").value(false))
                .andExpect(jsonPath("$.feasible").doesNotExist());
    }

    @Test
    void postValidWishlistReturns201WithFeasible() throws Exception {
        when(shoppingService.create(eq(USER_ID), any(ShoppingItemRequest.class))).thenReturn(WISHLIST_SAMPLE);

        mockMvc.perform(post("/api/shopping/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"listType": "WISHLIST", "name": "NAS", "estimatedPrice": 500.00, "envelopeId": 10, "priority": 1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.feasible").value(true))
                .andExpect(jsonPath("$.envelopeName").value("Ahorro"))
                .andExpect(jsonPath("$.envelopeBalance").value(800.00));
    }

    // --- list ---

    @Test
    void listWithoutFilterReturnsAll() throws Exception {
        when(shoppingService.list(USER_ID, null)).thenReturn(List.of(GROCERY_SAMPLE, WISHLIST_SAMPLE));

        mockMvc.perform(get("/api/shopping/items").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(shoppingService).list(USER_ID, null);
    }

    @Test
    void listWithListTypeFilterDelegatesToService() throws Exception {
        when(shoppingService.list(USER_ID, ShoppingListType.GROCERY)).thenReturn(List.of(GROCERY_SAMPLE));

        mockMvc.perform(get("/api/shopping/items")
                        .header("Authorization", bearer)
                        .param("listType", "GROCERY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(shoppingService).list(USER_ID, ShoppingListType.GROCERY);
    }

    // --- PATCH bought ---

    @Test
    void patchBoughtReturns200WithUpdatedState() throws Exception {
        ShoppingItemResponse checked = new ShoppingItemResponse(
                1L, ShoppingListType.GROCERY, "Leche", null, null, null, null,
                null, true, null, null, Instant.parse("2026-06-16T10:00:00Z"));

        when(shoppingService.setBought(USER_ID, 1L, true)).thenReturn(checked);

        mockMvc.perform(patch("/api/shopping/items/1/bought")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bought\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bought").value(true));
    }

    @Test
    void patchBoughtWithMissingFieldReturns400() throws Exception {
        mockMvc.perform(patch("/api/shopping/items/1/bought")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.bought").exists());
    }

    // --- 404 ---

    @Test
    void putNotFoundReturns404() throws Exception {
        when(shoppingService.update(eq(USER_ID), eq(99L), any(ShoppingItemRequest.class)))
                .thenThrow(new NotFoundException("Shopping item not found"));

        mockMvc.perform(put("/api/shopping/items/99")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"listType": "GROCERY", "name": "Leche"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void patchBoughtNotFoundReturns404() throws Exception {
        when(shoppingService.setBought(USER_ID, 99L, true))
                .thenThrow(new NotFoundException("Shopping item not found"));

        mockMvc.perform(patch("/api/shopping/items/99/bought")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bought\": true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // --- delete 204 ---

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/shopping/items/1").header("Authorization", bearer))
                .andExpect(status().isNoContent());

        verify(shoppingService).delete(USER_ID, 1L);
    }
}
