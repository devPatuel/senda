package dev.jordi.senda.investment;

import com.jayway.jsonpath.JsonPath;
import dev.jordi.senda.TestcontainersConfiguration;
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class InvestmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // Stub the only network-touching collaborator so tests never hit CoinGecko.
    @MockitoBean
    private CryptoPriceProvider cryptoPriceProvider;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void registerUsers() throws Exception {
        tokenA = register("usera@example.com", "User A");
        tokenB = register("userb@example.com", "User B");
    }

    private String register(String email, String name) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "password123", "name": "%s"}
                                """.formatted(email, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private long firstAssetClassId(String token, String name) throws Exception {
        String body = mockMvc.perform(get("/api/investments/asset-classes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Filter expressions return an array; take the single match's id
        JSONArray ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ((Number) ids.get(0)).longValue();
    }

    private long createHolding(String token, long assetClassId, String symbol, String name,
                               String quantity, String avgCost) throws Exception {
        String body = mockMvc.perform(post("/api/investments/holdings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assetClassId": %d, "symbol": "%s", "name": "%s", "quantity": %s, "avgCost": %s}
                                """.formatted(assetClassId, symbol, name, quantity, avgCost)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // --- default asset classes on registration ---

    @Test
    void registrationSeedsDefaultAssetClasses() throws Exception {
        mockMvc.perform(get("/api/investments/asset-classes")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].name", hasItem("Cripto")))
                .andExpect(jsonPath("$[*].name", hasItem("Fondos")))
                .andExpect(jsonPath("$[*].name", hasItem("Oro")))
                .andExpect(jsonPath("$[*].name", hasItem("Plata")))
                // Cripto's pricing source is CRYPTO; the metals are METAL
                .andExpect(jsonPath("$[?(@.name == 'Cripto')].pricingSource", hasItem("CRYPTO")))
                .andExpect(jsonPath("$[?(@.name == 'Oro')].pricingSource", hasItem("METAL")));
    }

    // --- holding lifecycle: create, buy, recompute, manual price -> market value / pnl ---

    @Test
    void holdingBuyRecomputesAverageCostAndManualPriceDrivesMarketValue() throws Exception {
        long cripto = firstAssetClassId(tokenA, "Cripto");
        long holding = createHolding(tokenA, cripto, "BTC", "Bitcoin", "2", "10000");

        // Buy 1 BTC @ 16000 -> qty 3, avg (2*10000 + 1*16000)/3 = 12000
        mockMvc.perform(post("/api/investments/holdings/" + holding + "/buys")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 1, "unitPrice": 16000, "date": "2026-06-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.avgCost").value(12000));

        // The buy is recorded as a lot
        mockMvc.perform(get("/api/investments/holdings/" + holding + "/lots")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].quantity").value(1))
                .andExpect(jsonPath("$[0].unitPrice").value(16000));

        // Set a manual price -> marketValue = 3 * 13000 = 39000; pnl = 39000 - 36000 = 3000
        mockMvc.perform(put("/api/investments/holdings/" + holding + "/price")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPrice": 13000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPrice").value(13000))
                .andExpect(jsonPath("$.lastPricedAt").exists())
                .andExpect(jsonPath("$.marketValue").value(39000))
                .andExpect(jsonPath("$.pnl").value(3000));
    }

    // --- delete asset class with holdings -> 409 ---

    @Test
    void deletingAssetClassWithHoldingsReturns409() throws Exception {
        long cripto = firstAssetClassId(tokenA, "Cripto");
        createHolding(tokenA, cripto, "BTC", "Bitcoin", "1", "10000");

        mockMvc.perform(delete("/api/investments/asset-classes/" + cripto)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isConflict());
    }

    // --- refresh prices uses the (mocked) crypto provider, never the network ---

    @Test
    void refreshPricesUpdatesCryptoHoldingFromProvider() throws Exception {
        when(cryptoPriceProvider.pricesInEur(Set.of("BTC"))).thenReturn(Map.of("BTC", new BigDecimal("20000")));

        long cripto = firstAssetClassId(tokenA, "Cripto");
        createHolding(tokenA, cripto, "BTC", "Bitcoin", "2", "10000");

        mockMvc.perform(post("/api/investments/refresh-prices")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentPrice").value(20000))
                .andExpect(jsonPath("$[0].marketValue").value(40000));
    }

    // --- NFT computed currentPurchaseValue ---

    @Test
    void nftReportsCurrentPurchaseValueFromCryptoPrice() throws Exception {
        when(cryptoPriceProvider.pricesInEur(Set.of("ETH"))).thenReturn(Map.of("ETH", new BigDecimal("3000")));

        mockMvc.perform(post("/api/investments/nfts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Punk", "collection": "CryptoPunks", "buyCryptoSymbol": "ETH",
                                 "buyCryptoAmount": 2, "fiatValueAtPurchase": 4000.00,
                                 "ourCurrentValue": 5000.00, "utility": "PFP"}
                                """))
                .andExpect(status().isCreated());

        // 2 ETH * 3000 = 6000
        mockMvc.perform(get("/api/investments/nfts").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].currentPurchaseValue").value(6000));
    }

    // --- KEY TEST: user isolation across asset classes, holdings and NFTs ---

    @Test
    void userBCannotSeeOrTouchUserAInvestments() throws Exception {
        long criptoA = firstAssetClassId(tokenA, "Cripto");
        long holdingA = createHolding(tokenA, criptoA, "BTC", "Bitcoin", "2", "10000");

        // B's holdings and NFTs are empty (B only has its own default asset classes)
        mockMvc.perform(get("/api/investments/holdings").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/investments/nfts").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // B cannot read, price, buy on or delete A's holding (404, not 403)
        mockMvc.perform(get("/api/investments/holdings/" + holdingA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/investments/holdings/" + holdingA + "/price")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPrice": 1}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/investments/holdings/" + holdingA + "/buys")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 1, "unitPrice": 1, "date": "2026-06-01"}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/investments/holdings/" + holdingA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // B cannot delete A's asset class either
        mockMvc.perform(delete("/api/investments/asset-classes/" + criptoA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // A still sees its holding intact
        mockMvc.perform(get("/api/investments/holdings/" + holdingA)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTC"));
    }

    // The only holding sub-resource not covered above: listing another user's lots.
    @Test
    void userBCannotListUserAHoldingLots() throws Exception {
        long criptoA = firstAssetClassId(tokenA, "Cripto");
        long holdingA = createHolding(tokenA, criptoA, "BTC", "Bitcoin", "2", "10000");

        mockMvc.perform(get("/api/investments/holdings/" + holdingA + "/lots")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }
}
