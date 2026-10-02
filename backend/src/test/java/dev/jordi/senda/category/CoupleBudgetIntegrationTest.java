package dev.jordi.senda.category;

import com.jayway.jsonpath.JsonPath;
import dev.jordi.senda.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wave E safety net: the couple budget shares one envelope per category
 * ({@code category_balances} is 1-to-1 with the category), is funded from the
 * couple accounts ({@code sumActiveBalanceBySpaceIds}), never leaks into a
 * member's personal budget, and is gated by space membership.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CoupleBudgetIntegrationTest {

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

    private void createAccount(String token, String name, String balance, Long spaceId) throws Exception {
        String space = spaceId == null ? "null" : spaceId.toString();
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "BANK", "balance": %s, "currency": "EUR", "spaceId": %s}
                                """.formatted(name, balance, space)))
                .andExpect(status().isCreated());
    }

    // Money reaches a space through its movements (a contribution here), not
    // through the hand-edited balance of its account.
    private void contribute(String token, long spaceId, String amount) throws Exception {
        long income = categoryId(token, spaceId, "Otros ingresos");
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "INCOME", "amount": %s, "date": "%s", "spaceId": %d}
                                """.formatted(income, amount, java.time.LocalDate.now(), spaceId)))
                .andExpect(status().isCreated());
    }

    private long categoryId(String token, Long spaceId, String name) throws Exception {
        String url = "/api/categories" + (spaceId == null ? "" : "?spaceId=" + spaceId);
        String body = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[?(@.name=='" + name + "')].id");
        assertThat(ids).as("category '%s' in scope %s", name, spaceId).isNotEmpty();
        return ids.get(0).longValue();
    }

    private String budget(String token, Long spaceId) throws Exception {
        String url = "/api/categories/budget" + (spaceId == null ? "" : "?spaceId=" + spaceId);
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String assign(String token, long categoryId, String amount, Long spaceId) throws Exception {
        String url = "/api/categories/" + categoryId + "/assign" + (spaceId == null ? "" : "?spaceId=" + spaceId);
        return mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": %s}
                                """.formatted(amount)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String setTarget(String token, long categoryId, String targetAmount, Long spaceId) throws Exception {
        String url = "/api/categories/" + categoryId + "/target" + (spaceId == null ? "" : "?spaceId=" + spaceId);
        return mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAmount": %s}
                                """.formatted(targetAmount)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static double num(String json, String path) {
        return ((Number) JsonPath.read(json, path)).doubleValue();
    }

    private static double categoryField(String json, String name, String field) {
        List<Object> vals = JsonPath.read(json, "$.categories[?(@.name=='" + name + "')]." + field);
        return ((Number) vals.get(0)).doubleValue();
    }

    // --- Test cases ---

    @Test
    void assignToCoupleCategoryFundedByCoupleAccounts() throws Exception {
        String tokenA = register("a@example.com", "User A");
        long spaceId = createSpace(tokenA, "Pareja");
        contribute(tokenA, spaceId, "300.00");
        long comida = categoryId(tokenA, spaceId, "Comida");

        String initial = budget(tokenA, spaceId);
        assertThat(num(initial, "$.totalAccounts")).isEqualTo(300.0);
        assertThat(num(initial, "$.toAssign")).isEqualTo(300.0);

        String afterAssign = assign(tokenA, comida, "120.00", spaceId);
        assertThat(num(afterAssign, "$.totalAssigned")).isEqualTo(120.0);
        assertThat(num(afterAssign, "$.toAssign")).isEqualTo(180.0);
        assertThat(categoryField(afterAssign, "Comida", "balance")).isEqualTo(120.0);

        String afterTarget = setTarget(tokenA, comida, "200.00", spaceId);
        assertThat(categoryField(afterTarget, "Comida", "targetAmount")).isEqualTo(200.0);
    }

    @Test
    void bothMembersSeeSameCoupleEnvelope() throws Exception {
        String tokenA = register("a@example.com", "User A");
        String tokenB = register("b@example.com", "User B");
        long spaceId = createSpace(tokenA, "Pareja");
        invite(tokenA, spaceId, "b@example.com");
        accept(tokenB, spaceId);

        contribute(tokenA, spaceId, "300.00");
        long comidaA = categoryId(tokenA, spaceId, "Comida");
        assign(tokenA, comidaA, "120.00", spaceId);

        // B sees the same shared envelope
        String bBudget = budget(tokenB, spaceId);
        assertThat(categoryField(bBudget, "Comida", "balance")).isEqualTo(120.0);

        // B tops it up; A sees the combined balance (same envelope)
        long comidaB = categoryId(tokenB, spaceId, "Comida");
        assign(tokenB, comidaB, "30.00", spaceId);

        String aBudget = budget(tokenA, spaceId);
        assertThat(categoryField(aBudget, "Comida", "balance")).isEqualTo(150.0);
    }

    @Test
    void coupleBudgetDoesNotLeakIntoPersonalBudget() throws Exception {
        String tokenA = register("a@example.com", "User A");
        long spaceId = createSpace(tokenA, "Pareja");
        createAccount(tokenA, "Personal", "1000.00", null);
        contribute(tokenA, spaceId, "300.00");
        long coupleComida = categoryId(tokenA, spaceId, "Comida");
        assign(tokenA, coupleComida, "120.00", spaceId);

        String personal = budget(tokenA, null);
        assertThat(num(personal, "$.totalAccounts")).isEqualTo(1000.0);
        assertThat(num(personal, "$.totalAssigned")).isZero();
        // the couple category id must not appear in the personal budget
        List<Number> ids = JsonPath.read(personal, "$.categories[*].id");
        assertThat(ids.stream().map(Number::longValue)).doesNotContain(coupleComida);
    }

    @Test
    void nonMemberCannotReadCoupleBudget() throws Exception {
        String tokenA = register("a@example.com", "User A");
        String tokenC = register("c@example.com", "User C");
        long spaceId = createSpace(tokenA, "Pareja");

        mockMvc.perform(get("/api/categories/budget?spaceId=" + spaceId)
                        .header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isNotFound());
    }
}
