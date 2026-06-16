package dev.jordi.senda.networth;

import com.jayway.jsonpath.JsonPath;
import dev.jordi.senda.TestcontainersConfiguration;
import dev.jordi.senda.investment.CryptoPriceProvider;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NetWorthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // Prevent integration tests from hitting CoinGecko during price refresh
    @MockitoBean
    private CryptoPriceProvider cryptoPriceProvider;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void registerUsers() throws Exception {
        tokenA = register("networth_usera@example.com", "NW User A");
        tokenB = register("networth_userb@example.com", "NW User B");
    }

    // --- helpers ---

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

    private long createAccount(String token, String name, String type, String balance) throws Exception {
        String body = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "%s", "balance": %s}
                                """.formatted(name, type, balance)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createDebt(String token, String direction, String counterparty,
                            String concept, String amount, String date) throws Exception {
        String body = mockMvc.perform(post("/api/debts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "direction": "%s",
                                  "counterparty": "%s",
                                  "concept": "%s",
                                  "originalAmount": %s,
                                  "date": "%s"
                                }
                                """.formatted(direction, counterparty, concept, amount, date)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void addPayment(String token, long debtId, String amount, String date) throws Exception {
        mockMvc.perform(post("/api/debts/" + debtId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": %s, "date": "%s"}
                                """.formatted(amount, date)))
                .andExpect(status().isCreated());
    }

    private long firstAssetClassId(String token, String name) throws Exception {
        String body = mockMvc.perform(get("/api/investments/asset-classes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
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

    private void setHoldingPrice(String token, long holdingId, String price) throws Exception {
        mockMvc.perform(put("/api/investments/holdings/" + holdingId + "/price")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPrice": %s}
                                """.formatted(price)))
                .andExpect(status().isOk());
    }

    private void createNft(String token, String name, String ourCurrentValue) throws Exception {
        mockMvc.perform(post("/api/investments/nfts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "collection": "TestCollection",
                                  "buyCryptoSymbol": "ETH",
                                  "buyCryptoAmount": 1.0,
                                  "fiatValueAtPurchase": 1000.00,
                                  "ourCurrentValue": %s,
                                  "utility": null
                                }
                                """.formatted(name, ourCurrentValue)))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // Main scenario
    // =========================================================================

    @Test
    void fullScenarioComputesCorrectNetWorth() throws Exception {
        // Liquid: bank 2000 + cash 500 = 2500
        createAccount(tokenA, "Banco", "BANK", "2000.00");
        createAccount(tokenA, "Efectivo", "CASH", "500.00");

        // Holdings: 2 BTC @ 30000 = 60000 (no-price holding ignored)
        long criptoId = firstAssetClassId(tokenA, "Cripto");
        long btcId = createHolding(tokenA, criptoId, "BTC", "Bitcoin", "2", "25000");
        setHoldingPrice(tokenA, btcId, "30000");
        // ETH without price -> contributes 0
        createHolding(tokenA, criptoId, "ETH", "Ethereum", "5", "2000");

        // NFT: ourCurrentValue = 1200
        createNft(tokenA, "CoolNFT", "1200.00");

        // Debt in favor: Pedro owes 200, paid 80 -> pending 120
        long debtFavor = createDebt(tokenA, "THEY_OWE_ME", "Pedro", "Cena", "200.00", "2026-06-01");
        addPayment(tokenA, debtFavor, "80.00", "2026-06-10");

        // Debt against: I owe Ana 500, paid 150 -> pending 350
        long debtAgainst = createDebt(tokenA, "I_OWE", "Ana", "Préstamo", "500.00", "2026-06-01");
        addPayment(tokenA, debtAgainst, "150.00", "2026-06-10");

        // Expected:
        // liquid = 2500.00
        // investmentsHoldings = 2 * 30000 = 60000.00
        // investmentsNfts = 1200.00
        // investments = 61200.00
        // debtsInFavor = 120.00
        // debtsAgainst = 350.00
        // net = 2500 + 61200 + 120 - 350 = 63470.00

        mockMvc.perform(get("/api/networth").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liquid").value(2500.00))
                .andExpect(jsonPath("$.investmentsHoldings").value(60000.00))
                .andExpect(jsonPath("$.investmentsNfts").value(1200.00))
                .andExpect(jsonPath("$.investments").value(61200.00))
                .andExpect(jsonPath("$.debtsInFavor").value(120.00))
                .andExpect(jsonPath("$.debtsAgainst").value(350.00))
                .andExpect(jsonPath("$.net").value(63470.00));
    }

    @Test
    void emptyUserReturnsAllZeros() throws Exception {
        mockMvc.perform(get("/api/networth").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liquid").value(0.00))
                .andExpect(jsonPath("$.investments").value(0.00))
                .andExpect(jsonPath("$.investmentsHoldings").value(0.00))
                .andExpect(jsonPath("$.investmentsNfts").value(0.00))
                .andExpect(jsonPath("$.debtsInFavor").value(0.00))
                .andExpect(jsonPath("$.debtsAgainst").value(0.00))
                .andExpect(jsonPath("$.net").value(0.00));
    }

    // =========================================================================
    // KEY TEST: user isolation — B's net worth does not include A's data
    // =========================================================================

    @Test
    void userBNetworthDoesNotIncludeUserAData() throws Exception {
        // A has a bank account and a debt in favor
        createAccount(tokenA, "Banco A", "BANK", "5000.00");
        long debtA = createDebt(tokenA, "THEY_OWE_ME", "Carlos", "Viaje", "300.00", "2026-06-01");
        addPayment(tokenA, debtA, "50.00", "2026-06-02");

        // B has only its own bank account
        createAccount(tokenB, "Banco B", "BANK", "100.00");

        // A sees its own data correctly
        mockMvc.perform(get("/api/networth").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liquid").value(5000.00))
                .andExpect(jsonPath("$.debtsInFavor").value(250.00));

        // B only sees its own 100 — A's 5000 and A's debts are invisible
        mockMvc.perform(get("/api/networth").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liquid").value(100.00))
                .andExpect(jsonPath("$.investments").value(0.00))
                .andExpect(jsonPath("$.debtsInFavor").value(0.00))
                .andExpect(jsonPath("$.debtsAgainst").value(0.00))
                .andExpect(jsonPath("$.net").value(100.00));
    }
}
