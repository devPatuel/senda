package dev.jordi.senda.shopping;

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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ShoppingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void registerUsers() throws Exception {
        tokenA = register("shopping-usera@example.com", "User A");
        tokenB = register("shopping-userb@example.com", "User B");
    }

    // -------------------------------------------------------------------------
    // Auth helpers
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Envelope helpers: an "envelope" is now an expense category. Assign a
    // balance to one of the seeded categories so ShoppingService can read it.
    // -------------------------------------------------------------------------

    /** Resolves a seeded expense category id by name. */
    private long expenseCategoryId(String token, String name) throws Exception {
        String body = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .param("type", "EXPENSE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name=='" + name + "')].id");
        return ids.get(0).longValue();
    }

    /**
     * Gives a seeded expense category a balance via the budget assign endpoint
     * and returns its id (the "envelope id" for shopping items).
     */
    private long setupEnvelopeWithBalance(String token, String categoryName, String amount) throws Exception {
        long categoryId = expenseCategoryId(token, categoryName);
        mockMvc.perform(post("/api/categories/" + categoryId + "/assign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": %s}
                                """.formatted(amount)))
                .andExpect(status().isOk());
        return categoryId;
    }

    private long createItem(String token, String body) throws Exception {
        String response = mockMvc.perform(post("/api/shopping/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    // -------------------------------------------------------------------------
    // Test: GROCERY check/uncheck round trip
    // -------------------------------------------------------------------------

    @Test
    void groceryCheckUncheckRoundTrip() throws Exception {
        long id = createItem(tokenA, """
                {"listType": "GROCERY", "name": "Leche"}
                """);

        // Initially not bought
        mockMvc.perform(get("/api/shopping/items")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("listType", "GROCERY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bought").value(false));

        // Mark as bought
        mockMvc.perform(patch("/api/shopping/items/" + id + "/bought")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bought\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bought").value(true));

        // Uncheck
        mockMvc.perform(patch("/api/shopping/items/" + id + "/bought")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bought\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bought").value(false));
    }

    // -------------------------------------------------------------------------
    // KEY TEST: feasible based on real envelope balance
    // -------------------------------------------------------------------------

    @Test
    void wishlistFeasibleReflectsRealEnvelopeBalance() throws Exception {
        // Assign 1000 € to "Comida"
        long envelopeId = setupEnvelopeWithBalance(tokenA, "Comida", "1000.00");

        // Create a wish that costs 800 € -> feasible=true (1000 >= 800)
        long itemId = createItem(tokenA, """
                {"listType": "WISHLIST", "name": "NAS", "estimatedPrice": 800.00, "envelopeId": %d, "priority": 1}
                """.formatted(envelopeId));

        mockMvc.perform(get("/api/shopping/items")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("listType", "WISHLIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(itemId))
                .andExpect(jsonPath("$[0].feasible").value(true))
                .andExpect(jsonPath("$[0].envelopeName").value("Comida"))
                .andExpect(jsonPath("$[0].envelopeBalance").value(1000.00));

        // Update price to 2000 € -> feasible=false (1000 < 2000)
        mockMvc.perform(put("/api/shopping/items/" + itemId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"listType": "WISHLIST", "name": "NAS", "estimatedPrice": 2000.00, "envelopeId": %d, "priority": 1}
                                """.formatted(envelopeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible").value(false));
    }

    // -------------------------------------------------------------------------
    // KEY TEST: user isolation
    // -------------------------------------------------------------------------

    @Test
    void userBCannotSeeOrTouchUserAItems() throws Exception {
        long envelopeA = setupEnvelopeWithBalance(tokenA, "Comida", "500.00");

        long idA = createItem(tokenA, """
                {"listType": "GROCERY", "name": "Pan de A"}
                """);

        // B's list is empty
        mockMvc.perform(get("/api/shopping/items").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // B cannot mark A's item as bought -> 404 (not 403)
        mockMvc.perform(patch("/api/shopping/items/" + idA + "/bought")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bought\": true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // B cannot update A's item -> 404
        mockMvc.perform(put("/api/shopping/items/" + idA)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"listType": "GROCERY", "name": "Hack"}
                                """))
                .andExpect(status().isNotFound());

        // B cannot delete A's item -> 404
        mockMvc.perform(delete("/api/shopping/items/" + idA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // B cannot create a wishlist item linked to A's envelope -> 404
        mockMvc.perform(post("/api/shopping/items")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"listType": "WISHLIST", "name": "NAS de B", "estimatedPrice": 100.00, "envelopeId": %d}
                                """.formatted(envelopeA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // A's item remains intact
        mockMvc.perform(get("/api/shopping/items").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Pan de A"));
    }

    // -------------------------------------------------------------------------
    // CRUD round trip + delete
    // -------------------------------------------------------------------------

    @Test
    void crudRoundTrip() throws Exception {
        long id = createItem(tokenA, """
                {"listType": "GROCERY", "name": "Mantequilla", "notes": "Sin sal"}
                """);

        mockMvc.perform(get("/api/shopping/items")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Mantequilla"))
                .andExpect(jsonPath("$[0].notes").value("Sin sal"));

        // Update
        mockMvc.perform(put("/api/shopping/items/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"listType": "GROCERY", "name": "Mantequilla ecológica"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mantequilla ecológica"))
                .andExpect(jsonPath("$.notes").isEmpty());

        // Delete
        mockMvc.perform(delete("/api/shopping/items/" + id)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/shopping/items").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
