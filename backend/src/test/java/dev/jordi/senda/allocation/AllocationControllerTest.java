package dev.jordi.senda.allocation;

import dev.jordi.senda.user.UserRepository;
import java.util.Optional;
import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.GlobalExceptionHandler;
import dev.jordi.senda.common.JwtAuthFilter;
import dev.jordi.senda.apitoken.ApiTokenService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AllocationController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
class AllocationControllerTest {

    @MockitoBean
    private ApiTokenService apiTokenService;

    @MockitoBean
    private UserRepository userRepository;

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AllocationService allocationService;

    private String bearer;

    @BeforeEach
    void setUp() {
        // The filter checks the token version against the stored one
        when(userRepository.findTokenVersionById(USER_ID)).thenReturn(Optional.of(0));
        bearer = "Bearer " + jwtService.generateToken(USER_ID);
    }

    private static final EnvelopeResponse SAMPLE_ENVELOPE =
            new EnvelopeResponse(1L, "Ahorro", "#10b981", new BigDecimal("50.00"), new BigDecimal("0.00"),
                    new BigDecimal("0.00"), new BigDecimal("0.00"));

    private static final String VALID_PLAN_BODY = """
            {
              "envelopes": [
                {"categoryId": 1, "percentage": 50},
                {"categoryId": 2, "percentage": 20},
                {"categoryId": 3, "percentage": 30}
              ]
            }
            """;

    // -------------------------------------------------------------------------
    // Auth guard
    // -------------------------------------------------------------------------

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/allocation/envelopes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // -------------------------------------------------------------------------
    // GET /api/allocation/envelopes
    // -------------------------------------------------------------------------

    @Test
    void listReturnsEnvelopes() throws Exception {
        when(allocationService.list(USER_ID)).thenReturn(List.of(SAMPLE_ENVELOPE));

        mockMvc.perform(get("/api/allocation/envelopes").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Ahorro"))
                .andExpect(jsonPath("$[0].balance").value(0.00));
    }

    // -------------------------------------------------------------------------
    // PUT /api/allocation/envelopes
    // -------------------------------------------------------------------------

    @Test
    void putPlanWithMissingEnvelopesFieldReturns400() throws Exception {
        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.envelopes").exists());
    }

    @Test
    void putPlanWithInvalidEnvelopeReturns400() throws Exception {
        // percentage missing
        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"envelopes": [{"categoryId": 1}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putPlanWherePercentagesDontSum100Returns409() throws Exception {
        when(allocationService.savePlan(eq(USER_ID), any(EnvelopePlanRequest.class)))
                .thenThrow(new ConflictException("Allocation percentages must sum to exactly 100"));

        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"envelopes": [{"categoryId": 1, "percentage": 50}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void putValidPlanReturns200WithList() throws Exception {
        when(allocationService.savePlan(eq(USER_ID), any(EnvelopePlanRequest.class)))
                .thenReturn(List.of(SAMPLE_ENVELOPE));

        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PLAN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ahorro"));
    }

    // -------------------------------------------------------------------------
    // POST /api/allocation/distribute
    // -------------------------------------------------------------------------

    @Test
    void distributeWithMissingAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/allocation/distribute")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"persist\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void distributeValidRequestReturns200() throws Exception {
        List<DistributionLine> lines = List.of(
                new DistributionLine(1L, "Ahorro", new BigDecimal("100.00"),
                        new BigDecimal("1000.00"), new BigDecimal("1000.00")));
        DistributionResponse response = new DistributionResponse(new BigDecimal("1000.00"), lines);

        when(allocationService.distribute(eq(USER_ID), any(DistributeRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/allocation/distribute")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1000.00, "persist": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.lines[0].envelopeName").value("Ahorro"))
                .andExpect(jsonPath("$.lines[0].allocated").value(1000.00));
    }

    @Test
    void distributeWithNoPlanReturns400() throws Exception {
        when(allocationService.distribute(eq(USER_ID), any(DistributeRequest.class)))
                .thenThrow(new InvalidAllocationException("No envelopes defined. Save a plan first."));

        mockMvc.perform(post("/api/allocation/distribute")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1000.00, "persist": false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
