package dev.jordi.senda.account;

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
class AccountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    void crudRoundTrip() throws Exception {
        long id = createAccount(tokenA, "Banco", "BANK", "1000.00");

        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Banco"))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].balance").value(1000.00));

        mockMvc.perform(put("/api/accounts/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Banco principal", "type": "BANK", "balance": 1234.56, "currency": "EUR"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Banco principal"))
                .andExpect(jsonPath("$.balance").value(1234.56));

        mockMvc.perform(delete("/api/accounts/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void archivingHidesAccountFromDefaultListAndTotalBalance() throws Exception {
        long bank = createAccount(tokenA, "Banco", "BANK", "1000.00");
        createAccount(tokenA, "Efectivo", "CASH", "50.00");

        // Total before archiving: 1050.00
        mockMvc.perform(get("/api/accounts/balance").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1050.00));

        // Archive the bank account
        mockMvc.perform(put("/api/accounts/" + bank)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Banco", "type": "BANK", "balance": 1000.00, "currency": "EUR", "archived": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        // Default list hides it; total drops to 50.00
        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/accounts/balance").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(50.00));

        // includeArchived shows both
        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void totalBalanceWithNoAccountsIsZero() throws Exception {
        mockMvc.perform(get("/api/accounts/balance").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    // --- KEY TEST: user isolation ---

    @Test
    void userBCannotSeeOrTouchUserAAccounts() throws Exception {
        long id = createAccount(tokenA, "Banco A", "BANK", "1000.00");

        // B's listing is empty and B's total balance excludes A's money
        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/accounts/balance").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        // PUT / DELETE on A's account return 404 for B (not 403)
        mockMvc.perform(put("/api/accounts/" + id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Hack", "type": "BANK", "balance": 0.00, "currency": "EUR"}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/accounts/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // A still sees its account intact
        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].balance").value(1000.00));
    }
}
