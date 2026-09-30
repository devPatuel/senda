package dev.jordi.senda.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordi.senda.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class QuickTransactionIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    @Test
    void quickAddUsesExplicitCategory() throws Exception {
        String token = registerAndGetToken("quick-a@test.dev");
        long categoryId = createCategory(token, "Comida quick");

        mvc.perform(post("/api/transactions/quick")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":12.50,\"description\":\"Mercadona\",\"categoryId\":" + categoryId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(12.50))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.categoryId").value((int) categoryId));
    }

    @Test
    void quickAddWithoutCategoryReturns400() throws Exception {
        String token = registerAndGetToken("quick-c@test.dev");
        mvc.perform(post("/api/transactions/quick")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":10,\"description\":\"algo sin categoria\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quickAddWithAnotherUsersCategoryReturns404() throws Exception {
        String tokenA = registerAndGetToken("quick-owner@test.dev");
        String tokenB = registerAndGetToken("quick-intruder@test.dev");
        long categoryA = createCategory(tokenA, "De A quick");

        mvc.perform(post("/api/transactions/quick")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":10,\"description\":\"x\",\"categoryId\":" + categoryA + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void quickAddWithAnIncomeCategoryReturns400() throws Exception {
        String token = registerAndGetToken("quick-income@test.dev");
        long incomeCategory = createCategory(token, "Nomina quick", "INCOME");

        // A quick capture is always an expense: it must not be filed under income
        mvc.perform(post("/api/transactions/quick")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":10,\"description\":\"x\",\"categoryId\":" + incomeCategory + "}"))
                .andExpect(status().isBadRequest());
    }

    private long createCategory(String token, String name) throws Exception {
        return createCategory(token, name, "EXPENSE");
    }

    private long createCategory(String token, String name, String type) throws Exception {
        String body = mvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "%s", "color": "#FF8800"}
                                """.formatted(name, type)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asLong();
    }

    private String registerAndGetToken(String email) throws Exception {
        String body = """
                {"name":"Test","email":"%s","password":"password123"}
                """.formatted(email);
        String response = mvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("token").asText();
    }
}
