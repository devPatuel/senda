package dev.jordi.senda.recurring;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RecurringPaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void registerUsers() throws Exception {
        tokenA = register("recurring-usera@example.com", "User A");
        tokenB = register("recurring-userb@example.com", "User B");
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

    private long expenseCategoryId(String token, String name) throws Exception {
        String body = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .param("type", "EXPENSE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name=='" + name + "')].id");
        return ids.get(0).longValue();
    }

    private long createPayment(String token, String body) throws Exception {
        String response = mockMvc.perform(post("/api/recurring")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    @Test
    void crudRoundTrip() throws Exception {
        long categoryId = expenseCategoryId(tokenA, "Ocio");

        long id = createPayment(tokenA, """
                {"name": "Netflix", "amount": 12.99, "frequency": "MONTHLY", "categoryId": %d, "dayOfMonth": 5}
                """.formatted(categoryId));

        mockMvc.perform(get("/api/recurring").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Netflix"))
                .andExpect(jsonPath("$[0].categoryName").value("Ocio"))
                .andExpect(jsonPath("$[0].monthlyEquivalent").value(12.99))
                .andExpect(jsonPath("$[0].nextDueDate").exists());

        mockMvc.perform(put("/api/recurring/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Netflix Premium", "amount": 17.99, "frequency": "MONTHLY", "categoryId": %d, "dayOfMonth": 5}
                                """.formatted(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Netflix Premium"))
                .andExpect(jsonPath("$.amount").value(17.99));

        mockMvc.perform(delete("/api/recurring/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/recurring").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void weeklyRoundTripDerivesNextDueDate() throws Exception {
        long categoryId = expenseCategoryId(tokenA, "Comida");

        createPayment(tokenA, """
                {"name": "Limpieza", "amount": 10.00, "frequency": "WEEKLY", "categoryId": %d, "dayOfMonth": 1, "dayOfWeek": 3}
                """.formatted(categoryId));

        mockMvc.perform(get("/api/recurring").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].frequency").value("WEEKLY"))
                .andExpect(jsonPath("$[0].dayOfWeek").value(3))
                .andExpect(jsonPath("$[0].monthlyEquivalent").value(43.33))  // 10 * 52 / 12
                .andExpect(jsonPath("$[0].nextDueDate").exists());
    }

    @Test
    void weeklyWithoutDayOfWeekIsRejected() throws Exception {
        long categoryId = expenseCategoryId(tokenA, "Comida");

        mockMvc.perform(post("/api/recurring")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Limpieza", "amount": 10.00, "frequency": "WEEKLY", "categoryId": %d, "dayOfMonth": 1}
                                """.formatted(categoryId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quarterlyRoundTripDerivesNextDueDate() throws Exception {
        long categoryId = expenseCategoryId(tokenA, "Salud");

        createPayment(tokenA, """
                {"name": "Cuota", "amount": 30.00, "frequency": "QUARTERLY", "categoryId": %d, "dayOfMonth": 10, "month": 2}
                """.formatted(categoryId));

        mockMvc.perform(get("/api/recurring").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].frequency").value("QUARTERLY"))
                .andExpect(jsonPath("$[0].month").value(2))
                .andExpect(jsonPath("$[0].monthlyEquivalent").value(10.00))  // 30 / 3
                .andExpect(jsonPath("$[0].nextDueDate").exists());
    }

    @Test
    void endDateRoundTrips() throws Exception {
        long categoryId = expenseCategoryId(tokenA, "Ocio");

        createPayment(tokenA, """
                {"name": "Netflix", "amount": 12.99, "frequency": "MONTHLY", "categoryId": %d, "dayOfMonth": 5, "endDate": "2026-12-31"}
                """.formatted(categoryId));

        mockMvc.perform(get("/api/recurring").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].endDate").value("2026-12-31"));
    }

    @Test
    void annualWithoutMonthIsRejected() throws Exception {
        long categoryId = expenseCategoryId(tokenA, "Salud");

        mockMvc.perform(post("/api/recurring")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Seguro", "amount": 600.00, "frequency": "ANNUAL", "categoryId": %d, "dayOfMonth": 10}
                                """.formatted(categoryId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listIsOrderedByNextDueDate() throws Exception {
        long ocio = expenseCategoryId(tokenA, "Ocio");

        // Annual far away (December) and monthly soon (day 1) — order by computed due date
        createPayment(tokenA, """
                {"name": "Seguro", "amount": 600.00, "frequency": "ANNUAL", "categoryId": %d, "dayOfMonth": 25, "month": 12}
                """.formatted(ocio));
        createPayment(tokenA, """
                {"name": "Spotify", "amount": 9.99, "frequency": "MONTHLY", "categoryId": %d, "dayOfMonth": 1}
                """.formatted(ocio));

        // The list must be sorted ascending by nextDueDate (the first item is the soonest)
        String body = mockMvc.perform(get("/api/recurring").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String first = JsonPath.read(body, "$[0].nextDueDate");
        String second = JsonPath.read(body, "$[1].nextDueDate");
        org.assertj.core.api.Assertions.assertThat(first.compareTo(second)).isLessThanOrEqualTo(0);
    }

    @Test
    void userBCannotUseUserAsCategoryOrTouchItems() throws Exception {
        long categoryA = expenseCategoryId(tokenA, "Ocio");
        long idA = createPayment(tokenA, """
                {"name": "Netflix", "amount": 12.99, "frequency": "MONTHLY", "categoryId": %d, "dayOfMonth": 5}
                """.formatted(categoryA));

        // B's list is empty
        mockMvc.perform(get("/api/recurring").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // B cannot create a payment against A's category -> 404
        mockMvc.perform(post("/api/recurring")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Hack", "amount": 1.00, "frequency": "MONTHLY", "categoryId": %d, "dayOfMonth": 1}
                                """.formatted(categoryA)))
                .andExpect(status().isNotFound());

        // B cannot delete A's payment -> 404
        mockMvc.perform(delete("/api/recurring/" + idA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }
}
