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

    private static String planBody(String... pairs) {
        // pairs: name, percentage alternating
        StringBuilder sb = new StringBuilder("{\"envelopes\": [");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append("{\"name\": \"").append(pairs[i])
              .append("\", \"percentage\": ").append(pairs[i + 1]).append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Save a valid plan and verify the response
    // -------------------------------------------------------------------------

    @Test
    void saveValidPlanAndRetrieveEnvelopes() throws Exception {
        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody("Ahorro", "50", "Inversión", "20", "Ocio", "30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Ahorro"))
                .andExpect(jsonPath("$[0].percentage").value(50))
                .andExpect(jsonPath("$[0].balance").value(0))
                .andExpect(jsonPath("$[2].name").value("Ocio"));

        mockMvc.perform(get("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    // -------------------------------------------------------------------------
    // Plan that does not sum to 100 is rejected with 409
    // -------------------------------------------------------------------------

    @Test
    void planNotSummingTo100IsRejectedWith409() throws Exception {
        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody("Ahorro", "50", "Ocio", "30")))  // sum = 80
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // -------------------------------------------------------------------------
    // Distribute with persist accumulates balances
    // -------------------------------------------------------------------------

    @Test
    void distributeWithPersistAccumulatesBalances() throws Exception {
        // Save a plan first
        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody("Ahorro", "60", "Ocio", "40")))
                .andExpect(status().isOk());

        // Distribute 1000 € with persist=true
        mockMvc.perform(post("/api/allocation/distribute")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1000.00, \"persist\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].envelopeName").value("Ahorro"))
                .andExpect(jsonPath("$.lines[0].allocated").value(600.00))
                .andExpect(jsonPath("$.lines[0].balance").value(600.00))
                .andExpect(jsonPath("$.lines[1].allocated").value(400.00))
                .andExpect(jsonPath("$.lines[1].balance").value(400.00));

        // Balances should be accumulated after a second distribution
        mockMvc.perform(post("/api/allocation/distribute")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 500.00, \"persist\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].balance").value(900.00))
                .andExpect(jsonPath("$.lines[1].balance").value(600.00));

        // GET envelopes should reflect the new balances
        mockMvc.perform(get("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$[0].balance").value(900.00))
                .andExpect(jsonPath("$[1].balance").value(600.00));
    }

    // -------------------------------------------------------------------------
    // Edit plan preserving balances by id
    // -------------------------------------------------------------------------

    @Test
    void editPlanPreservesExistingBalancesByEnvelopeId() throws Exception {
        // Create initial plan
        String createBody = mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody("Ahorro", "60", "Ocio", "40")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long ahorroId = ((Number) JsonPath.read(createBody, "$[0].id")).longValue();

        // Distribute so that balances are non-zero
        mockMvc.perform(post("/api/allocation/distribute")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1000.00, \"persist\": true}"))
                .andExpect(status().isOk());

        // Edit plan: keep "Ahorro" (by id), rename it, change percentage, add new envelope
        String editBody = """
                {
                  "envelopes": [
                    {"id": %d, "name": "Ahorro renovado", "percentage": 50},
                    {"name": "Inversión", "percentage": 30},
                    {"name": "Ocio", "percentage": 20}
                  ]
                }
                """.formatted(ahorroId);

        String editResult = mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Ahorro renovado"))
                // Balance must be preserved from before the rename
                .andExpect(jsonPath("$[0].balance").value(600.00))
                .andReturn().getResponse().getContentAsString();

        // New envelopes start at 0
        assertJsonPath(editResult, "$[1].balance", 0.0);
        assertJsonPath(editResult, "$[2].balance", 0.0);
    }

    // -------------------------------------------------------------------------
    // KEY TEST: user isolation
    // -------------------------------------------------------------------------

    @Test
    void userBCannotSeeOrEditUserAsPlan() throws Exception {
        // A saves a plan
        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody("Ahorro", "100")))
                .andExpect(status().isOk());

        // B's envelope list is empty
        mockMvc.perform(get("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // B cannot distribute (no plan defined)
        mockMvc.perform(post("/api/allocation/distribute")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 500.00, \"persist\": false}"))
                .andExpect(status().isBadRequest());

        // A's plan is untouched
        mockMvc.perform(get("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // B cannot steal A's envelope id by passing it in a plan save
        String listA = mockMvc.perform(get("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenA))
                .andReturn().getResponse().getContentAsString();
        long aEnvelopeId = ((Number) JsonPath.read(listA, "$[0].id")).longValue();

        mockMvc.perform(put("/api/allocation/envelopes")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"envelopes": [{"id": %d, "name": "Hack", "percentage": 100}]}
                                """.formatted(aEnvelopeId)))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static void assertJsonPath(String json, String path, double expected) {
        double actual = ((Number) JsonPath.read(json, path)).doubleValue();
        if (Math.abs(actual - expected) > 0.001) {
            throw new AssertionError("Expected " + path + " to be " + expected + " but was " + actual);
        }
    }
}
