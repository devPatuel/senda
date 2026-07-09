package dev.jordi.senda.transaction;

import dev.jordi.senda.common.GlobalExceptionHandler;
import dev.jordi.senda.common.JwtAuthFilter;
import dev.jordi.senda.common.JwtService;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.SecurityConfig;
import dev.jordi.senda.common.TransactionType;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
class TransactionControllerTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TransactionService transactionService;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtService.generateToken(USER_ID);
    }

    private static final TransactionResponse SAMPLE = new TransactionResponse(
            10L, 5L, "Comida", "#EF4444", TransactionType.EXPENSE,
            new BigDecimal("12.50"), LocalDate.of(2026, 6, 10), "Lunch",
            Instant.parse("2026-06-10T12:00:00Z"));

    private static final String VALID_BODY = """
            {"categoryId": 5, "type": "EXPENSE", "amount": 12.50, "date": "2026-06-10", "description": "Lunch"}
            """;

    // --- security ---

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // --- POST validations ---

    @Test
    void postWithMissingFieldsReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.categoryId").exists())
                .andExpect(jsonPath("$.fieldErrors.type").exists())
                .andExpect(jsonPath("$.fieldErrors.amount").exists())
                .andExpect(jsonPath("$.fieldErrors.date").exists());
    }

    @Test
    void postWithNegativeAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": 5, "type": "EXPENSE", "amount": -3.00, "date": "2026-06-10"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void postWithMoreThanTwoDecimalsReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": 5, "type": "EXPENSE", "amount": 3.123, "date": "2026-06-10"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void postWithTooLongDescriptionReturns400() throws Exception {
        String body = """
                {"categoryId": 5, "type": "EXPENSE", "amount": 3.00, "date": "2026-06-10", "description": "%s"}
                """.formatted("x".repeat(501));
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").exists());
    }

    @Test
    void postValidReturns201WithBody() throws Exception {
        when(transactionService.create(eq(USER_ID), any(TransactionRequest.class))).thenReturn(SAMPLE);

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.categoryId").value(5))
                .andExpect(jsonPath("$.categoryName").value("Comida"))
                .andExpect(jsonPath("$.categoryColor").value("#EF4444"))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.amount").value(12.50))
                .andExpect(jsonPath("$.date").value("2026-06-10"))
                .andExpect(jsonPath("$.description").value("Lunch"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void postWithTypeMismatchReturns400WithMessage() throws Exception {
        when(transactionService.create(eq(USER_ID), any(TransactionRequest.class)))
                .thenThrow(new InvalidTransactionException(
                        "Transaction type EXPENSE does not match category type INCOME"));

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Transaction type EXPENSE does not match category type INCOME"));
    }

    // --- GET list ---

    @Test
    void listUsesDefaultPagination() throws Exception {
        when(transactionService.list(eq(USER_ID), isNull(), eq(0), eq(20), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new PageResponse<>(List.of(SAMPLE), 0, 20, 1, 1));

        mockMvc.perform(get("/api/transactions").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(transactionService).list(USER_ID, null, 0, 20, null, null, null, null);
    }

    @Test
    void listPassesFiltersToService() throws Exception {
        when(transactionService.list(eq(USER_ID), isNull(), eq(1), eq(50),
                eq(LocalDate.of(2026, 6, 1)), eq(LocalDate.of(2026, 6, 30)),
                eq(5L), eq(TransactionType.EXPENSE)))
                .thenReturn(new PageResponse<>(List.of(), 1, 50, 0, 0));

        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", bearer)
                        .param("page", "1").param("size", "50")
                        .param("from", "2026-06-01").param("to", "2026-06-30")
                        .param("categoryId", "5").param("type", "EXPENSE"))
                .andExpect(status().isOk());

        verify(transactionService).list(USER_ID, null, 1, 50,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 5L, TransactionType.EXPENSE);
    }

    @Test
    void listWithInvalidPagingReturns400() throws Exception {
        when(transactionService.list(eq(USER_ID), isNull(), eq(0), eq(101), isNull(), isNull(), isNull(), isNull()))
                .thenThrow(new InvalidTransactionException("size must be between 1 and 100"));

        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", bearer)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
    }

    @Test
    void listWithNonNumericPageReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", bearer)
                        .param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    // --- GET / PUT / DELETE by id ---

    @Test
    void getByIdReturns200() throws Exception {
        when(transactionService.get(USER_ID, 10L)).thenReturn(SAMPLE);

        mockMvc.perform(get("/api/transactions/10").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void getByIdNotFoundReturns404() throws Exception {
        when(transactionService.get(USER_ID, 99L)).thenThrow(new NotFoundException("Transaction not found"));

        mockMvc.perform(get("/api/transactions/99").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void putValidReturns200() throws Exception {
        when(transactionService.update(eq(USER_ID), eq(10L), any(TransactionRequest.class)))
                .thenReturn(SAMPLE);

        mockMvc.perform(put("/api/transactions/10")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/transactions/10").header("Authorization", bearer))
                .andExpect(status().isNoContent());

        verify(transactionService).delete(USER_ID, 10L);
    }

    // --- summary ---

    @Test
    void summaryReturnsContractShape() throws Exception {
        when(transactionService.summary(USER_ID, null, 2026, 6)).thenReturn(new MonthlySummaryResponse(
                2026, 6, new BigDecimal("1500.00"), new BigDecimal("120.75"), new BigDecimal("1379.25"),
                List.of(new CategorySummary(5L, "Comida", "#EF4444", TransactionType.EXPENSE,
                        new BigDecimal("100.50")))));

        mockMvc.perform(get("/api/transactions/summary")
                        .header("Authorization", bearer)
                        .param("year", "2026").param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.month").value(6))
                .andExpect(jsonPath("$.totalIncome").value(1500.00))
                .andExpect(jsonPath("$.totalExpense").value(120.75))
                .andExpect(jsonPath("$.balance").value(1379.25))
                .andExpect(jsonPath("$.byCategory[0].categoryId").value(5))
                .andExpect(jsonPath("$.byCategory[0].categoryName").value("Comida"))
                .andExpect(jsonPath("$.byCategory[0].categoryColor").value("#EF4444"))
                .andExpect(jsonPath("$.byCategory[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$.byCategory[0].total").value(100.50));
    }

    @Test
    void summaryWithMissingParamsReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions/summary")
                        .header("Authorization", bearer)
                        .param("year", "2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryWithInvalidMonthReturns400() throws Exception {
        when(transactionService.summary(anyLong(), isNull(), eq(2026), eq(13)))
                .thenThrow(new InvalidTransactionException("month must be between 1 and 12"));

        mockMvc.perform(get("/api/transactions/summary")
                        .header("Authorization", bearer)
                        .param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("month must be between 1 and 12"));
    }
}
