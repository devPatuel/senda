package dev.jordi.senda.account;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
class AccountControllerTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AccountService accountService;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtService.generateToken(USER_ID);
    }

    private static final AccountResponse SAMPLE = new AccountResponse(
            10L, "Banco principal", AccountType.BANK, new BigDecimal("1500.00"), "EUR", false,
            Instant.parse("2026-06-10T12:00:00Z"));

    private static final String VALID_BODY = """
            {"name": "Banco principal", "type": "BANK", "balance": 1500.00, "currency": "EUR"}
            """;

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void postWithMissingFieldsReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.type").exists())
                .andExpect(jsonPath("$.fieldErrors.balance").exists());
    }

    @Test
    void postWithInvalidCurrencyReturns400() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Banco", "type": "BANK", "balance": 10.00, "currency": "euro"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currency").exists());
    }

    @Test
    void postWithMoreThanTwoDecimalsReturns400() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Banco", "type": "BANK", "balance": 10.123}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.balance").exists());
    }

    @Test
    void postValidReturns201WithBody() throws Exception {
        when(accountService.create(eq(USER_ID), any(AccountRequest.class))).thenReturn(SAMPLE);

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Banco principal"))
                .andExpect(jsonPath("$.type").value("BANK"))
                .andExpect(jsonPath("$.balance").value(1500.00))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void listReturnsAccounts() throws Exception {
        when(accountService.list(USER_ID, false)).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get("/api/accounts").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));

        verify(accountService).list(USER_ID, false);
    }

    @Test
    void listPassesIncludeArchived() throws Exception {
        when(accountService.list(USER_ID, true)).thenReturn(List.of());

        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", bearer)
                        .param("includeArchived", "true"))
                .andExpect(status().isOk());

        verify(accountService).list(USER_ID, true);
    }

    @Test
    void balanceReturnsTotal() throws Exception {
        when(accountService.totalBalance(USER_ID)).thenReturn(new TotalBalanceResponse(new BigDecimal("1550.00")));

        mockMvc.perform(get("/api/accounts/balance").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1550.00));
    }

    @Test
    void putValidReturns200() throws Exception {
        when(accountService.update(eq(USER_ID), eq(10L), any(AccountUpdateRequest.class))).thenReturn(SAMPLE);

        mockMvc.perform(put("/api/accounts/10")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void putNotFoundReturns404() throws Exception {
        when(accountService.update(eq(USER_ID), eq(99L), any(AccountUpdateRequest.class)))
                .thenThrow(new NotFoundException("Account not found"));

        mockMvc.perform(put("/api/accounts/99")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/accounts/10").header("Authorization", bearer))
                .andExpect(status().isNoContent());

        verify(accountService).delete(USER_ID, 10L);
    }
}
