package dev.jordi.senda.shoppinglist;

import dev.jordi.senda.apitoken.ApiTokenService;
import dev.jordi.senda.common.GlobalExceptionHandler;
import dev.jordi.senda.common.JwtAuthFilter;
import dev.jordi.senda.common.JwtService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShoppingListController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
class ShoppingListControllerTest {

    private static final Long USER_ID = 1L;

    @MockitoBean private ApiTokenService apiTokenService;
    @MockitoBean private ShoppingListService service;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtService.generateToken(USER_ID);
    }

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/shopping-list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutProductIdReturns400() throws Exception {
        mockMvc.perform(post("/api/shopping-list")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.productId").exists());
    }

    @Test
    void postValidReturns201() throws Exception {
        when(service.add(eq(USER_ID), any(AddToListRequest.class))).thenReturn(
                new ShoppingListItemResponse(1L, 9L, "Leche", 2, false,
                        new BigDecimal("1.10"), "Lidl", new BigDecimal("2.20")));
        mockMvc.perform(post("/api/shopping-list")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":9,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productName").value("Leche"))
                .andExpect(jsonPath("$.lineTotal").value(2.20));
    }

    @Test
    void postDuplicateReturns400() throws Exception {
        when(service.add(eq(USER_ID), any(AddToListRequest.class)))
                .thenThrow(new InvalidShoppingListException("Product already in the list"));
        mockMvc.perform(post("/api/shopping-list")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
