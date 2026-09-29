package dev.jordi.senda.investment;

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

@WebMvcTest(InvestmentController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
class InvestmentControllerTest {

    @MockitoBean
    private ApiTokenService apiTokenService;

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private InvestmentService investmentService;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtService.generateToken(USER_ID);
    }

    private static final HoldingResponse SAMPLE_HOLDING = new HoldingResponse(
            10L, 5L, "Cripto", PricingSource.CRYPTO, "BTC", "Bitcoin",
            new BigDecimal("2"), new BigDecimal("10000"), new BigDecimal("15000"),
            Instant.parse("2026-06-10T12:00:00Z"), new BigDecimal("30000"),
            new BigDecimal("10000"), new BigDecimal("20000"), new BigDecimal("50.00"), BigDecimal.ZERO);

    private static final AssetClassResponse SAMPLE_ASSET_CLASS = new AssetClassResponse(
            5L, "Cripto", PricingSource.CRYPTO, Instant.parse("2026-06-10T12:00:00Z"));

    // --- auth ---

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/investments/holdings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // --- asset classes ---

    @Test
    void createAssetClassWithMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/investments/asset-classes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.pricingSource").exists());
    }

    @Test
    void createAssetClassValidReturns201() throws Exception {
        when(investmentService.createAssetClass(eq(USER_ID), any(AssetClassRequest.class)))
                .thenReturn(SAMPLE_ASSET_CLASS);

        mockMvc.perform(post("/api/investments/asset-classes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Cripto", "pricingSource": "CRYPTO"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.pricingSource").value("CRYPTO"));
    }

    // --- holdings ---

    @Test
    void listHoldingsReturnsComputedFigures() throws Exception {
        when(investmentService.listHoldings(USER_ID, null)).thenReturn(List.of(SAMPLE_HOLDING));

        mockMvc.perform(get("/api/investments/holdings").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("BTC"))
                .andExpect(jsonPath("$[0].marketValue").value(30000))
                .andExpect(jsonPath("$[0].pnl").value(10000));

        verify(investmentService).listHoldings(USER_ID, null);
    }

    @Test
    void createHoldingWithNegativeQuantityReturns400() throws Exception {
        mockMvc.perform(post("/api/investments/holdings")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assetClassId": 5, "symbol": "BTC", "name": "Bitcoin", "quantity": -1, "avgCost": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity").exists());
    }

    @Test
    void createHoldingValidReturns201() throws Exception {
        when(investmentService.createHolding(eq(USER_ID), any(HoldingRequest.class)))
                .thenReturn(SAMPLE_HOLDING);

        mockMvc.perform(post("/api/investments/holdings")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assetClassId": 5, "symbol": "BTC", "name": "Bitcoin", "quantity": 2, "avgCost": 10000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void addBuyWithNonPositiveQuantityReturns400() throws Exception {
        mockMvc.perform(post("/api/investments/holdings/10/buys")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 0, "unitPrice": 100, "date": "2026-06-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity").exists());
    }

    @Test
    void addBuyValidReturns201() throws Exception {
        when(investmentService.addBuy(eq(USER_ID), eq(10L), any(BuyRequest.class)))
                .thenReturn(SAMPLE_HOLDING);

        mockMvc.perform(post("/api/investments/holdings/10/buys")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 1, "unitPrice": 16000, "date": "2026-06-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void createHoldingViolatingBusinessRuleReturns400() throws Exception {
        when(investmentService.createHolding(eq(USER_ID), any(HoldingRequest.class)))
                .thenThrow(new InvalidInvestmentException("avgCost must be 0 when quantity is 0"));

        mockMvc.perform(post("/api/investments/holdings")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assetClassId": 5, "symbol": "BTC", "name": "Bitcoin", "quantity": 0, "avgCost": 100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("avgCost must be 0 when quantity is 0"));
    }

    @Test
    void getHoldingNotFoundReturns404() throws Exception {
        when(investmentService.getHolding(USER_ID, 99L))
                .thenThrow(new NotFoundException("Holding not found"));

        mockMvc.perform(get("/api/investments/holdings/99").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteHoldingReturns204() throws Exception {
        mockMvc.perform(delete("/api/investments/holdings/10").header("Authorization", bearer))
                .andExpect(status().isNoContent());

        verify(investmentService).deleteHolding(USER_ID, 10L);
    }

    // --- nfts ---

    @Test
    void createNftWithMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/investments/nfts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.buyCryptoSymbol").exists());
    }
}
