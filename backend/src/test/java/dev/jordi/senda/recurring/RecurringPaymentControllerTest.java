package dev.jordi.senda.recurring;

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
import java.time.LocalDate;
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

@WebMvcTest(RecurringPaymentController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
class RecurringPaymentControllerTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private RecurringPaymentService service;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtService.generateToken(USER_ID);
    }

    private static final RecurringPaymentResponse SAMPLE = new RecurringPaymentResponse(
            1L, "Netflix", new BigDecimal("12.99"), RecurringFrequency.MONTHLY,
            7L, "Suscripciones", "#3b82f6", 1, null,
            LocalDate.of(2026, 7, 1), new BigDecimal("12.99"));

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/recurring"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void postWithMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/recurring")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.amount").exists())
                .andExpect(jsonPath("$.fieldErrors.frequency").exists())
                .andExpect(jsonPath("$.fieldErrors.categoryId").exists())
                .andExpect(jsonPath("$.fieldErrors.dayOfMonth").exists());
    }

    @Test
    void postWithDayOutOfRangeReturns400() throws Exception {
        mockMvc.perform(post("/api/recurring")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X", "amount": 10.00, "frequency": "MONTHLY", "categoryId": 7, "dayOfMonth": 40}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dayOfMonth").exists());
    }

    @Test
    void postValidReturns201() throws Exception {
        when(service.create(eq(USER_ID), any(RecurringPaymentRequest.class))).thenReturn(SAMPLE);

        mockMvc.perform(post("/api/recurring")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Netflix", "amount": 12.99, "frequency": "MONTHLY", "categoryId": 7, "dayOfMonth": 1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Netflix"))
                .andExpect(jsonPath("$.nextDueDate").value("2026-07-01"))
                .andExpect(jsonPath("$.monthlyEquivalent").value(12.99));
    }

    @Test
    void annualWithoutMonthReturns400() throws Exception {
        when(service.create(eq(USER_ID), any(RecurringPaymentRequest.class)))
                .thenThrow(new InvalidRecurringException("month is required for annual payments"));

        mockMvc.perform(post("/api/recurring")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Seguro", "amount": 600.00, "frequency": "ANNUAL", "categoryId": 7, "dayOfMonth": 10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void listReturnsPayments() throws Exception {
        when(service.list(USER_ID)).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get("/api/recurring").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Netflix"));
    }

    @Test
    void putNotFoundReturns404() throws Exception {
        when(service.update(eq(USER_ID), eq(99L), any(RecurringPaymentRequest.class)))
                .thenThrow(new NotFoundException("Recurring payment not found"));

        mockMvc.perform(put("/api/recurring/99")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X", "amount": 10.00, "frequency": "MONTHLY", "categoryId": 7, "dayOfMonth": 1}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/recurring/1").header("Authorization", bearer))
                .andExpect(status().isNoContent());

        verify(service).delete(USER_ID, 1L);
    }
}
