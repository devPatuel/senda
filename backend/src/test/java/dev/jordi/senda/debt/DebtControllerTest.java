package dev.jordi.senda.debt;

import dev.jordi.senda.common.GlobalExceptionHandler;
import dev.jordi.senda.common.JwtAuthFilter;
import dev.jordi.senda.apitoken.ApiTokenService;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DebtController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
class DebtControllerTest {

    @MockitoBean
    private ApiTokenService apiTokenService;

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private DebtService debtService;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtService.generateToken(USER_ID);
    }

    private static final DebtResponse SAMPLE_DEBT = new DebtResponse(
            10L, DebtDirection.THEY_OWE_ME, "Ana", "Cena del viernes",
            new BigDecimal("100.00"), new BigDecimal("30.00"), new BigDecimal("70.00"),
            false, LocalDate.of(2026, 6, 10), Instant.parse("2026-06-10T12:00:00Z"));

    private static final DebtPaymentResponse SAMPLE_PAYMENT = new DebtPaymentResponse(
            1L, 10L, new BigDecimal("30.00"), LocalDate.of(2026, 6, 11), "Transferencia",
            Instant.parse("2026-06-11T10:00:00Z"));

    private static final String VALID_DEBT_BODY = """
            {
              "direction": "THEY_OWE_ME",
              "counterparty": "Ana",
              "concept": "Cena del viernes",
              "originalAmount": 100.00,
              "date": "2026-06-10"
            }
            """;

    private static final String VALID_PAYMENT_BODY = """
            {
              "amount": 30.00,
              "date": "2026-06-11",
              "note": "Transferencia"
            }
            """;

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/debts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // -------------------------------------------------------------------------
    // Validation 400
    // -------------------------------------------------------------------------

    @Test
    void postWithMissingFieldsReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/debts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.direction").exists())
                .andExpect(jsonPath("$.fieldErrors.counterparty").exists())
                .andExpect(jsonPath("$.fieldErrors.concept").exists())
                .andExpect(jsonPath("$.fieldErrors.originalAmount").exists())
                .andExpect(jsonPath("$.fieldErrors.date").exists());
    }

    @Test
    void postWithNegativeAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/debts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "direction": "I_OWE",
                                  "counterparty": "Banco",
                                  "concept": "Préstamo",
                                  "originalAmount": -50.00,
                                  "date": "2026-06-10"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.originalAmount").exists());
    }

    // -------------------------------------------------------------------------
    // Create 201
    // -------------------------------------------------------------------------

    @Test
    void postValidReturns201WithBody() throws Exception {
        when(debtService.create(eq(USER_ID), any(DebtRequest.class))).thenReturn(SAMPLE_DEBT);

        mockMvc.perform(post("/api/debts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_DEBT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.direction").value("THEY_OWE_ME"))
                .andExpect(jsonPath("$.counterparty").value("Ana"))
                .andExpect(jsonPath("$.originalAmount").value(100.00))
                .andExpect(jsonPath("$.paidAmount").value(30.00))
                .andExpect(jsonPath("$.pendingAmount").value(70.00))
                .andExpect(jsonPath("$.settled").value(false));
    }

    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------

    @Test
    void listReturnsDebts() throws Exception {
        when(debtService.list(USER_ID, null, null)).thenReturn(List.of(SAMPLE_DEBT));

        mockMvc.perform(get("/api/debts").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));

        verify(debtService).list(USER_ID, null, null);
    }

    @Test
    void listWithDirectionFilterPassesItToService() throws Exception {
        when(debtService.list(USER_ID, DebtDirection.THEY_OWE_ME, null)).thenReturn(List.of(SAMPLE_DEBT));

        mockMvc.perform(get("/api/debts")
                        .header("Authorization", bearer)
                        .param("direction", "THEY_OWE_ME"))
                .andExpect(status().isOk());

        verify(debtService).list(USER_ID, DebtDirection.THEY_OWE_ME, null);
    }

    // -------------------------------------------------------------------------
    // Get single
    // -------------------------------------------------------------------------

    @Test
    void getReturnsDebt() throws Exception {
        when(debtService.get(USER_ID, 10L)).thenReturn(SAMPLE_DEBT);

        mockMvc.perform(get("/api/debts/10").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void getForeignDebtReturns404() throws Exception {
        when(debtService.get(USER_ID, 99L)).thenThrow(new NotFoundException("Debt not found"));

        mockMvc.perform(get("/api/debts/99").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    @Test
    void putValidReturns200() throws Exception {
        when(debtService.update(eq(USER_ID), eq(10L), any(DebtRequest.class))).thenReturn(SAMPLE_DEBT);

        mockMvc.perform(put("/api/debts/10")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_DEBT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/debts/10").header("Authorization", bearer))
                .andExpect(status().isNoContent());

        verify(debtService).delete(USER_ID, 10L);
    }

    // -------------------------------------------------------------------------
    // Payments
    // -------------------------------------------------------------------------

    @Test
    void addPaymentReturns201() throws Exception {
        when(debtService.addPayment(eq(USER_ID), eq(10L), any(DebtPaymentRequest.class)))
                .thenReturn(SAMPLE_PAYMENT);

        mockMvc.perform(post("/api/debts/10/payments")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYMENT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.debtId").value(10))
                .andExpect(jsonPath("$.amount").value(30.00));
    }

    @Test
    void addPaymentExceedingPendingReturns400WithMessage() throws Exception {
        when(debtService.addPayment(eq(USER_ID), eq(10L), any(DebtPaymentRequest.class)))
                .thenThrow(new InvalidDebtException("Payment exceeds the pending amount"));

        mockMvc.perform(post("/api/debts/10/payments")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYMENT_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Payment exceeds the pending amount"));
    }

    @Test
    void addPaymentMissingAmountReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/debts/10/payments")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"2026-06-11\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void addPaymentToForeignDebtReturns404() throws Exception {
        when(debtService.addPayment(eq(USER_ID), eq(99L), any(DebtPaymentRequest.class)))
                .thenThrow(new NotFoundException("Debt not found"));

        mockMvc.perform(post("/api/debts/99/payments")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYMENT_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void listPaymentsReturnsOrderedList() throws Exception {
        when(debtService.listPayments(USER_ID, 10L)).thenReturn(List.of(SAMPLE_PAYMENT));

        mockMvc.perform(get("/api/debts/10/payments").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void deletePaymentReturns204() throws Exception {
        mockMvc.perform(delete("/api/debts/10/payments/1").header("Authorization", bearer))
                .andExpect(status().isNoContent());

        verify(debtService).deletePayment(USER_ID, 10L, 1L);
    }

    @Test
    void deletePaymentForeignReturns404() throws Exception {
        doThrow(new NotFoundException("Payment not found"))
                .when(debtService).deletePayment(USER_ID, 10L, 99L);

        mockMvc.perform(delete("/api/debts/10/payments/99").header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }
}
