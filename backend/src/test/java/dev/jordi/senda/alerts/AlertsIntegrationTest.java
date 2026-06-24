package dev.jordi.senda.alerts;

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

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AlertsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String token;

    @BeforeEach
    void registerUser() throws Exception {
        token = register("alerts-user@example.com", "Alerts User");
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

    private long expenseCategoryId(String name) throws Exception {
        String body = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .param("type", "EXPENSE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name=='" + name + "')].id");
        return ids.get(0).longValue();
    }

    private void createExpense(long categoryId, String amount, LocalDate date) throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "EXPENSE", "amount": %s, "date": "%s"}
                                """.formatted(categoryId, amount, date)))
                .andExpect(status().isCreated());
    }

    private void createRecurring(long categoryId, String name) throws Exception {
        mockMvc.perform(post("/api/recurring")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "amount": 9.99, "frequency": "MONTHLY", "categoryId": %d, "dayOfMonth": 1}
                                """.formatted(name, categoryId)))
                .andExpect(status().isCreated());
    }

    @Test
    void freshUserHasNoAlerts() throws Exception {
        mockMvc.perform(get("/api/alerts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.antExpenses.length()").value(0))
                .andExpect(jsonPath("$.forgottenSubscriptions.length()").value(0));
    }

    @Test
    void detectsAntExpensesAndForgottenSubscriptions() throws Exception {
        long comida = expenseCategoryId("Comida");
        long salud = expenseCategoryId("Salud");
        LocalDate today = LocalDate.now();

        // 8 small expenses in Comida this month: count 8, sum 56, avg 7 -> ant alert
        for (int i = 0; i < 8; i++) {
            createExpense(comida, "7.00", today);
        }

        // Recurring in Salud (no Salud expenses in 2 months) -> forgotten.
        // Recurring in Comida (used this month) -> NOT forgotten.
        createRecurring(salud, "Revista");
        createRecurring(comida, "Compra semanal");

        mockMvc.perform(get("/api/alerts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.antExpenses.length()").value(1))
                .andExpect(jsonPath("$.antExpenses[0].categoryName").value("Comida"))
                .andExpect(jsonPath("$.antExpenses[0].count").value(8))
                .andExpect(jsonPath("$.antExpenses[0].total").value(56.00))
                .andExpect(jsonPath("$.forgottenSubscriptions.length()").value(1))
                .andExpect(jsonPath("$.forgottenSubscriptions[0].name").value("Revista"))
                .andExpect(jsonPath("$.forgottenSubscriptions[0].categoryName").value("Salud"));
    }

    @Test
    void belowThresholdDoesNotTriggerAntExpense() throws Exception {
        long comida = expenseCategoryId("Comida");
        LocalDate today = LocalDate.now();

        // Only 7 small expenses -> below the count threshold of 8
        for (int i = 0; i < 7; i++) {
            createExpense(comida, "7.00", today);
        }

        mockMvc.perform(get("/api/alerts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.antExpenses.length()").value(0));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isUnauthorized());
    }
}
