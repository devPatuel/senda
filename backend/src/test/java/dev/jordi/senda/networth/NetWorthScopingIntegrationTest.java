package dev.jordi.senda.networth;

import com.jayway.jsonpath.JsonPath;
import dev.jordi.senda.TestcontainersConfiguration;
import dev.jordi.senda.investment.CryptoPriceProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end safety net for Wave C: each member's net worth includes 50% of the
 * balance of their couple spaces' active accounts ({@code coupleShare}), the two
 * members see the same share over the same account, archived couple accounts drop
 * out, users without a space are unaffected, and the daily snapshot persists it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NetWorthScopingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // Prevent integration tests from hitting CoinGecko during price refresh
    @MockitoBean
    private CryptoPriceProvider cryptoPriceProvider;

    // --- HTTP helpers ---

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

    private long createSpace(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/spaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void invite(String ownerToken, long spaceId, String email) throws Exception {
        mockMvc.perform(post("/api/spaces/" + spaceId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }

    private void accept(String token, long spaceId) throws Exception {
        mockMvc.perform(post("/api/spaces/" + spaceId + "/accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    /** Creates an account (personal when spaceId is null) and returns its id. */
    private long createAccount(String token, String name, String balance, Long spaceId) throws Exception {
        String space = spaceId == null ? "null" : spaceId.toString();
        String body = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "BANK", "balance": %s, "currency": "EUR", "spaceId": %s}
                                """.formatted(name, balance, space)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Archives an account (PUT with archived=true); type/balance echo the couple account. */
    private void archiveAccount(String token, long accountId, String name, String balance) throws Exception {
        mockMvc.perform(put("/api/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "BANK", "balance": %s, "currency": "EUR", "archived": true}
                                """.formatted(name, balance)))
                .andExpect(status().isOk());
    }

    // --- Test cases ---

    @Test
    void netWorthIncludesHalfOfCoupleAccounts() throws Exception {
        String tokenA = register("a@example.com", "User A");
        long spaceId = createSpace(tokenA, "Pareja");

        createAccount(tokenA, "Personal", "1000.00", null);
        createAccount(tokenA, "Común", "400.00", spaceId);

        mockMvc.perform(get("/api/networth").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liquid").value(1000.00))
                .andExpect(jsonPath("$.coupleShare").value(200.00))
                .andExpect(jsonPath("$.net").value(1200.00));
    }

    @Test
    void bothMembersSeeSameCoupleShare() throws Exception {
        String tokenA = register("a@example.com", "User A");
        String tokenB = register("her@example.com", "User B");
        long spaceId = createSpace(tokenA, "Pareja");
        invite(tokenA, spaceId, "her@example.com");
        accept(tokenB, spaceId);

        createAccount(tokenA, "Común", "400.00", spaceId);

        // Same 400 couple account -> each member counts their own 200 (never doubled)
        mockMvc.perform(get("/api/networth").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupleShare").value(200.00))
                .andExpect(jsonPath("$.net").value(200.00));

        mockMvc.perform(get("/api/networth").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupleShare").value(200.00))
                .andExpect(jsonPath("$.net").value(200.00));
    }

    @Test
    void archivedCoupleAccountsExcluded() throws Exception {
        String tokenA = register("a@example.com", "User A");
        long spaceId = createSpace(tokenA, "Pareja");

        long coupleAccount = createAccount(tokenA, "Común", "400.00", spaceId);
        archiveAccount(tokenA, coupleAccount, "Común", "400.00");

        mockMvc.perform(get("/api/networth").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupleShare").value(0.00))
                .andExpect(jsonPath("$.net").value(0.00));
    }

    @Test
    void userWithoutSpaceHasZeroCoupleShareAndUnchangedNet() throws Exception {
        String tokenC = register("c@example.com", "User C");

        createAccount(tokenC, "Personal", "500.00", null);

        mockMvc.perform(get("/api/networth").header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liquid").value(500.00))
                .andExpect(jsonPath("$.coupleShare").value(0.00))
                .andExpect(jsonPath("$.net").value(500.00));
    }

    @Test
    void snapshotPersistsCoupleShare() throws Exception {
        String tokenA = register("a@example.com", "User A");
        long spaceId = createSpace(tokenA, "Pareja");

        createAccount(tokenA, "Común", "400.00", spaceId);

        // Loading net worth records today's snapshot with the couple share
        mockMvc.perform(get("/api/networth").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupleShare").value(200.00));

        mockMvc.perform(get("/api/networth/history")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].coupleShare").value(200.00));
    }
}
