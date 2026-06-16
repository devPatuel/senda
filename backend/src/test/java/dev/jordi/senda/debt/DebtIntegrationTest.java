package dev.jordi.senda.debt;

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
class DebtIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void registerUsers() throws Exception {
        tokenA = register("debt_usera@example.com", "Debt User A");
        tokenB = register("debt_userb@example.com", "Debt User B");
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

    private long addPayment(String token, long debtId, String amount, String date) throws Exception {
        String body = mockMvc.perform(post("/api/debts/" + debtId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": %s, "date": "%s"}
                                """.formatted(amount, date)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // =========================================================================
    // KEY TEST: user isolation
    // =========================================================================

    @Test
    void userBCannotSeeOrTouchUserADebts() throws Exception {
        long idA = createDebt(tokenA, "THEY_OWE_ME", "Carlos", "Viaje", "200.00", "2026-06-01");

        // B's listing is empty
        mockMvc.perform(get("/api/debts").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // GET on A's debt returns 404 for B (not 403)
        mockMvc.perform(get("/api/debts/" + idA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // PUT on A's debt returns 404 for B
        mockMvc.perform(put("/api/debts/" + idA)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "direction": "I_OWE",
                                  "counterparty": "Hack",
                                  "concept": "Hack",
                                  "originalAmount": 1.00,
                                  "date": "2026-06-01"
                                }
                                """))
                .andExpect(status().isNotFound());

        // DELETE on A's debt returns 404 for B
        mockMvc.perform(delete("/api/debts/" + idA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // POST payment to A's debt returns 404 for B
        mockMvc.perform(post("/api/debts/" + idA + "/payments")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "date": "2026-06-02"}
                                """))
                .andExpect(status().isNotFound());

        // A still sees its own debt intact
        mockMvc.perform(get("/api/debts").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].originalAmount").value(200.00));
    }

    // =========================================================================
    // Partial payments, auto-settle, un-settle
    // =========================================================================

    @Test
    void partialPaymentsThenSettledThenUnsettle() throws Exception {
        long debtId = createDebt(tokenA, "THEY_OWE_ME", "Laura", "Préstamo", "100.00", "2026-06-01");

        // First payment: 30 -> pending 70, not settled
        addPayment(tokenA, debtId, "30.00", "2026-06-02");

        mockMvc.perform(get("/api/debts/" + debtId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(30.00))
                .andExpect(jsonPath("$.pendingAmount").value(70.00))
                .andExpect(jsonPath("$.settled").value(false));

        // Second payment: 70 -> pending 0, settled true
        long payment2Id = addPayment(tokenA, debtId, "70.00", "2026-06-03");

        mockMvc.perform(get("/api/debts/" + debtId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(100.00))
                .andExpect(jsonPath("$.pendingAmount").value(0.00))
                .andExpect(jsonPath("$.settled").value(true));

        // Delete the second payment: settled reverts to false
        mockMvc.perform(delete("/api/debts/" + debtId + "/payments/" + payment2Id)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/debts/" + debtId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(30.00))
                .andExpect(jsonPath("$.pendingAmount").value(70.00))
                .andExpect(jsonPath("$.settled").value(false));
    }

    @Test
    void paymentExceedingPendingReturns400() throws Exception {
        long debtId = createDebt(tokenA, "I_OWE", "Banco", "Préstamo", "100.00", "2026-06-01");
        addPayment(tokenA, debtId, "30.00", "2026-06-02");

        // Try to pay 80 when only 70 remains
        mockMvc.perform(post("/api/debts/" + debtId + "/payments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 80.00, "date": "2026-06-03"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Payment exceeds the pending amount")));
    }

    // =========================================================================
    // CRUD round trip
    // =========================================================================

    @Test
    void crudRoundTrip() throws Exception {
        long id = createDebt(tokenA, "THEY_OWE_ME", "Pedro", "Cena", "50.00", "2026-06-10");

        // List contains the new debt
        mockMvc.perform(get("/api/debts").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].counterparty").value("Pedro"));

        // Update
        mockMvc.perform(put("/api/debts/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "direction": "THEY_OWE_ME",
                                  "counterparty": "Pedro Actualizado",
                                  "concept": "Cena actualizada",
                                  "originalAmount": 60.00,
                                  "date": "2026-06-11"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counterparty").value("Pedro Actualizado"))
                .andExpect(jsonPath("$.originalAmount").value(60.00));

        // Delete
        mockMvc.perform(delete("/api/debts/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/debts").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void filterByDirectionReturnsOnlyMatchingDebts() throws Exception {
        createDebt(tokenA, "THEY_OWE_ME", "Ana", "Cena", "20.00", "2026-06-01");
        createDebt(tokenA, "I_OWE", "Banco", "Tarjeta", "500.00", "2026-06-01");

        mockMvc.perform(get("/api/debts")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("direction", "THEY_OWE_ME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].counterparty").value("Ana"));

        mockMvc.perform(get("/api/debts")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("settled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
