package dev.jordi.senda.space;

import com.jayway.jsonpath.JsonPath;
import dev.jordi.senda.TestcontainersConfiguration;
import dev.jordi.senda.category.DefaultCategories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end safety net for Wave B: proves the non-leak invariant — no couple
 * ({@code space_id} non-null) row ever surfaces in a personal view (summary,
 * balance, budget, listings) — and that space resources are gated by membership.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SpaceScopingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    /** Creates a category (personal when spaceId is null) and returns its id. */
    private long createCategory(String token, String name, Long spaceId) throws Exception {
        String space = spaceId == null ? "null" : spaceId.toString();
        String body = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "EXPENSE", "color": "#EF4444", "spaceId": %s}
                                """.formatted(name, space)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Fetches the id of a category with the given name in the given scope. */
    private long categoryId(String token, Long spaceId, String name) throws Exception {
        String url = "/api/categories" + (spaceId == null ? "" : "?spaceId=" + spaceId);
        String body = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[?(@.name=='" + name + "')].id");
        assertThat(ids).as("category '%s' in scope %s", name, spaceId).isNotEmpty();
        return ids.get(0).longValue();
    }

    private long createTransaction(String token, long categoryId, String amount, String date,
                                   Long spaceId) throws Exception {
        String space = spaceId == null ? "null" : spaceId.toString();
        String body = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "EXPENSE", "amount": %s, "date": "%s",
                                 "description": "x", "spaceId": %s}
                                """.formatted(categoryId, amount, date, space)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // --- Test cases ---

    @Test
    void coupleTransactionDoesNotLeakIntoPersonalSummary() throws Exception {
        String tokenA = register("a@example.com", "User A");
        long spaceId = createSpace(tokenA, "Pareja");

        long personalCat = categoryId(tokenA, null, "Comida");
        long spaceCat = categoryId(tokenA, spaceId, "Comida");

        createTransaction(tokenA, personalCat, "100.00", "2026-01-15", null);
        createTransaction(tokenA, spaceCat, "50.00", "2026-01-20", spaceId);

        String personal = mockMvc.perform(get("/api/transactions/summary?year=2026&month=1")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(new BigDecimal(JsonPath.read(personal, "$.totalExpense").toString()))
                .isEqualByComparingTo("100.00");

        String space = mockMvc.perform(get("/api/transactions/summary?spaceId=" + spaceId + "&year=2026&month=1")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(new BigDecimal(JsonPath.read(space, "$.totalExpense").toString()))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void coupleAccountDoesNotLeakIntoPersonalBalanceNorBudget() throws Exception {
        String tokenA = register("a@example.com", "User A");
        long spaceId = createSpace(tokenA, "Pareja");

        createAccount(tokenA, "Personal", "1000.00", null);
        long coupleAccount = createAccount(tokenA, "Común", "500.00", spaceId);

        mockMvc.perform(get("/api/accounts/balance").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1000.00));

        mockMvc.perform(get("/api/categories/budget").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAccounts").value(1000.00));

        mockMvc.perform(get("/api/accounts?spaceId=" + spaceId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + coupleAccount + ")]").exists());

        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + coupleAccount + ")]").doesNotExist());
    }

    @Test
    void spaceCreationSeedsDefaultCategories() throws Exception {
        String tokenA = register("a@example.com", "User A");
        long spaceId = createSpace(tokenA, "Pareja");

        mockMvc.perform(get("/api/categories?spaceId=" + spaceId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(DefaultCategories.ALL.size()));
    }

    @Test
    void nonMemberCannotUseSpaceResources() throws Exception {
        String tokenA = register("a@example.com", "User A");
        String tokenC = register("c@example.com", "User C");
        long spaceId = createSpace(tokenA, "Pareja");
        long spaceCat = categoryId(tokenA, spaceId, "Comida");

        mockMvc.perform(get("/api/accounts?spaceId=" + spaceId).header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "EXPENSE", "amount": 20.00,
                                 "date": "2026-01-10", "description": "x", "spaceId": %d}
                                """.formatted(spaceCat, spaceId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void bothMembersSeeSameCoupleTransactions() throws Exception {
        String tokenA = register("a@example.com", "User A");
        String tokenB = register("her@example.com", "User B");
        long spaceId = createSpace(tokenA, "Pareja");
        invite(tokenA, spaceId, "her@example.com");
        accept(tokenB, spaceId);

        long spaceCat = categoryId(tokenA, spaceId, "Comida");
        long txId = createTransaction(tokenA, spaceCat, "30.00", "2026-01-05", spaceId);

        mockMvc.perform(get("/api/transactions?spaceId=" + spaceId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + txId + ")]").exists());
    }

    @Test
    void personalTransactionRejectsSpaceCategory() throws Exception {
        String tokenA = register("a@example.com", "User A");
        long spaceId = createSpace(tokenA, "Pareja");
        long spaceCat = categoryId(tokenA, spaceId, "Comida");

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "EXPENSE", "amount": 20.00,
                                 "date": "2026-01-10", "description": "x", "spaceId": null}
                                """.formatted(spaceCat)))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateCategoryNameAllowedAcrossScopes() throws Exception {
        String tokenA = register("a@example.com", "User A");
        long spaceId = createSpace(tokenA, "Pareja");

        createCategory(tokenA, "Regalos", null);
        createCategory(tokenA, "Regalos", spaceId);

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Regalos", "type": "EXPENSE", "color": "#EF4444", "spaceId": null}
                                """))
                .andExpect(status().isConflict());
    }
}
