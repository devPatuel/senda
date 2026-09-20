package dev.jordi.senda.allocation;

import com.jayway.jsonpath.JsonPath;
import dev.jordi.senda.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AllocationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void registerUsers() throws Exception {
        tokenA = register("alloc-usera@example.com", "User A");
        tokenB = register("alloc-userb@example.com", "User B");
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

    private static String planBody(long[] categoryIds, String[] percentages) {
        StringBuilder sb = new StringBuilder("{\"envelopes\": [");
        for (int i = 0; i < categoryIds.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"categoryId\": ").append(categoryIds[i])
              .append(", \"percentage\": ").append(percentages[i]).append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Returns the id of the seeded EXPENSE category with the given name, read
     * from the live allocation envelope listing (envelope id == category id).
     */
    private long expenseCategoryId(String token, String name) throws Exception {
        String body = mockMvc.perform(get("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // A name filter returns a single-element list of ids.
        java.util.List<Number> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.get(0).longValue();
    }

    // -------------------------------------------------------------------------
    // Envelopes carry what is left, not just what was put in
    // -------------------------------------------------------------------------

    @Test
    void envelopeShowsWhatIsLeftAfterSpending() throws Exception {
        long comida = expenseCategoryId(tokenA, "Comida");

        mockMvc.perform(post("/api/categories/" + comida + "/assign")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 400.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "EXPENSE", "amount": 120.00, "date": "%s", "description": "Súper"}
                                """.formatted(comida, java.time.LocalDate.now())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Comida"))
                .andExpect(jsonPath("$[0].balance").value(400.00))
                .andExpect(jsonPath("$[0].spent").value(120.00))
                .andExpect(jsonPath("$[0].available").value(280.00));
    }

    // -------------------------------------------------------------------------
    // Save a valid plan and verify the response (over seeded expense categories)
    // -------------------------------------------------------------------------

    @Test
    void saveValidPlanAndRetrieveEnvelopes() throws Exception {
        long comida = expenseCategoryId(tokenA, "Comida");
        long ocio = expenseCategoryId(tokenA, "Ocio");
        long salud = expenseCategoryId(tokenA, "Salud");

        // Sorted by name, the 7 seeded expense categories are returned; the plan
        // sets a target on three of them. Comida is alphabetically first.
        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(new long[]{comida, ocio, salud},
                                new String[]{"50", "20", "30"})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].name").value("Comida"))
                .andExpect(jsonPath("$[0].percentage").value(50))
                .andExpect(jsonPath("$[0].balance").value(0));

        // Re-reading shows the same 7 categories (sorted by name) with their targets.
        // Order: Comida(0), Compras(1), Ocio(2), Otros gastos(3), Salud(4),
        // Transporte(5), Vivienda(6).
        mockMvc.perform(get("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].name").value("Comida"))
                .andExpect(jsonPath("$[0].percentage").value(50))
                .andExpect(jsonPath("$[2].name").value("Ocio"))
                .andExpect(jsonPath("$[2].percentage").value(20))
                .andExpect(jsonPath("$[4].name").value("Salud"))
                .andExpect(jsonPath("$[4].percentage").value(30))
                // Categories not in the plan report a 0 target
                .andExpect(jsonPath("$[1].name").value("Compras"))
                .andExpect(jsonPath("$[1].percentage").value(0));
    }

    // -------------------------------------------------------------------------
    // Plan that does not sum to 100 is rejected with 409
    // -------------------------------------------------------------------------

    @Test
    void planNotSummingTo100IsRejectedWith409() throws Exception {
        long comida = expenseCategoryId(tokenA, "Comida");
        long ocio = expenseCategoryId(tokenA, "Ocio");

        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(new long[]{comida, ocio},
                                new String[]{"50", "30"})))  // sum = 80
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // -------------------------------------------------------------------------
    // Distribute with persist accumulates balances on the categories
    // -------------------------------------------------------------------------

    @Test
    void distributeWithPersistAccumulatesBalances() throws Exception {
        long comida = expenseCategoryId(tokenA, "Comida");
        long ocio = expenseCategoryId(tokenA, "Ocio");

        // Plan: Comida 60% / Ocio 40% (both seeded expense categories)
        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(new long[]{comida, ocio}, new String[]{"60", "40"})))
                .andExpect(status().isOk());

        // Distribute 1000 € with persist=true. Lines come back in plan order
        // (active expense categories with a target, sorted by name): Comida, Ocio.
        mockMvc.perform(post("/api/allocation/distribute")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1000.00, \"persist\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].envelopeName").value("Comida"))
                .andExpect(jsonPath("$.lines[0].allocated").value(600.00))
                .andExpect(jsonPath("$.lines[0].balance").value(600.00))
                .andExpect(jsonPath("$.lines[1].envelopeName").value("Ocio"))
                .andExpect(jsonPath("$.lines[1].allocated").value(400.00))
                .andExpect(jsonPath("$.lines[1].balance").value(400.00));

        // A second distribution accumulates on top of the existing balances
        mockMvc.perform(post("/api/allocation/distribute")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 500.00, \"persist\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].balance").value(900.00))
                .andExpect(jsonPath("$.lines[1].balance").value(600.00));

        // GET envelopes reflects the new balances on the categories (Comida=0, Ocio=2)
        mockMvc.perform(get("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$[0].name").value("Comida"))
                .andExpect(jsonPath("$[0].balance").value(900.00))
                .andExpect(jsonPath("$[2].name").value("Ocio"))
                .andExpect(jsonPath("$[2].balance").value(600.00));

        // The budget view shows the same balances (single source of truth)
        mockMvc.perform(get("/api/categories/budget")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssigned").value(1500.00))
                .andExpect(jsonPath("$.categories[0].name").value("Comida"))
                .andExpect(jsonPath("$.categories[0].balance").value(900.00))
                .andExpect(jsonPath("$.categories[2].name").value("Ocio"))
                .andExpect(jsonPath("$.categories[2].balance").value(600.00));
    }

    // -------------------------------------------------------------------------
    // Editing the plan preserves the balance that lives on the category
    // -------------------------------------------------------------------------

    @Test
    void editPlanPreservesExistingBalanceOnCategory() throws Exception {
        long comida = expenseCategoryId(tokenA, "Comida");
        long ocio = expenseCategoryId(tokenA, "Ocio");
        long salud = expenseCategoryId(tokenA, "Salud");

        // Initial plan: Comida 60 / Ocio 40, then distribute so balances are non-zero
        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(new long[]{comida, ocio}, new String[]{"60", "40"})))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/allocation/distribute")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1000.00, \"persist\": true}"))
                .andExpect(status().isOk());

        // Edit plan: change Comida's percentage and swap Ocio for Salud.
        // Comida keeps its 600 € balance because the balance lives on the category,
        // independent of the target percentage.
        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(new long[]{comida, salud}, new String[]{"50", "50"})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                // Comida (index 0): new target 50, balance preserved at 600
                .andExpect(jsonPath("$[0].name").value("Comida"))
                .andExpect(jsonPath("$[0].percentage").value(50))
                .andExpect(jsonPath("$[0].balance").value(600.00))
                // Ocio (index 2) dropped from the plan: target back to 0, balance preserved
                .andExpect(jsonPath("$[2].name").value("Ocio"))
                .andExpect(jsonPath("$[2].percentage").value(0))
                .andExpect(jsonPath("$[2].balance").value(400.00))
                // Salud (index 4) newly added to the plan starts at a zero balance
                .andExpect(jsonPath("$[4].name").value("Salud"))
                .andExpect(jsonPath("$[4].percentage").value(50))
                .andExpect(jsonPath("$[4].balance").value(0));
    }

    // -------------------------------------------------------------------------
    // KEY TEST: user isolation
    // -------------------------------------------------------------------------

    @Test
    void userBCannotUseUserAsCategoriesOrDistributeWithoutPlan() throws Exception {
        long comidaA = expenseCategoryId(tokenA, "Comida");

        // A saves a 100% plan on Comida
        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(new long[]{comidaA}, new String[]{"100"})))
                .andExpect(status().isOk());

        // B also sees 7 (its own) expense categories, all with 0% target
        mockMvc.perform(get("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[?(@.percentage > 0)]").doesNotExist());

        // B cannot distribute: it has no plan (no category with a target)
        mockMvc.perform(post("/api/allocation/distribute")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 500.00, \"persist\": false}"))
                .andExpect(status().isBadRequest());

        // B cannot save a plan that references A's category id -> 404
        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(new long[]{comidaA}, new String[]{"100"})))
                .andExpect(status().isNotFound());

        // A's plan is untouched: Comida (index 0) still at 100%
        mockMvc.perform(get("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Comida"))
                .andExpect(jsonPath("$[0].percentage").value(100));
    }

}
