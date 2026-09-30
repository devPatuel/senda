package dev.jordi.senda.wishlist;

import dev.jordi.senda.user.UserRepository;
import java.util.Optional;
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
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WishlistController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
class WishlistControllerTest {

    private static final Long USER_ID = 1L;

    @MockitoBean private ApiTokenService apiTokenService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private WishlistService wishlistService;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    private String bearer;

    @BeforeEach
    void setUp() {
        // The filter checks the token version against the stored one
        when(userRepository.findTokenVersionById(USER_ID)).thenReturn(Optional.of(0));
        bearer = "Bearer " + jwtService.generateToken(USER_ID);
    }

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/wishlist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/wishlist")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void postValidReturns201() throws Exception {
        when(wishlistService.create(eq(USER_ID), any(WishlistItemRequest.class))).thenReturn(
                new WishlistItemResponse(7L, "NAS", null, "https://x", "nota",
                        new BigDecimal("500.00"), 1, Instant.parse("2026-07-11T10:00:00Z")));
        mockMvc.perform(post("/api/wishlist")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NAS\",\"price\":500.00,\"priority\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("NAS"));
    }
}
