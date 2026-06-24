package dev.jordi.senda.categoryrule;

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
class CategoryRuleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void registerUsers() throws Exception {
        tokenA = register("rule-usera@example.com", "User A");
        tokenB = register("rule-userb@example.com", "User B");
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

    private long categoryId(String token, String name) throws Exception {
        String body = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .param("type", "EXPENSE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name=='" + name + "')].id");
        return ids.get(0).longValue();
    }

    private long createRule(String token, String matchText, long categoryId) throws Exception {
        String body = mockMvc.perform(post("/api/category-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matchText": "%s", "categoryId": %d}
                                """.formatted(matchText, categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void crudRoundTrip() throws Exception {
        long comida = categoryId(tokenA, "Comida");
        long id = createRule(tokenA, "MERCADONA", comida);

        mockMvc.perform(get("/api/category-rules").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].matchText").value("MERCADONA"))
                .andExpect(jsonPath("$[0].categoryName").value("Comida"));

        mockMvc.perform(put("/api/category-rules/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matchText": "Mercadona Online", "categoryId": %d}
                                """.formatted(comida)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchText").value("Mercadona Online"));

        mockMvc.perform(delete("/api/category-rules/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/category-rules").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void duplicateMatchTextIsRejected() throws Exception {
        long comida = categoryId(tokenA, "Comida");
        createRule(tokenA, "MERCADONA", comida);

        mockMvc.perform(post("/api/category-rules")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matchText": "MERCADONA", "categoryId": %d}
                                """.formatted(comida)))
                .andExpect(status().isConflict());
    }

    @Test
    void ruleAgainstForeignCategoryReturns404() throws Exception {
        long comidaA = categoryId(tokenA, "Comida");

        mockMvc.perform(post("/api/category-rules")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matchText": "X", "categoryId": %d}
                                """.formatted(comidaA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rulesAreIsolatedByUser() throws Exception {
        long comida = categoryId(tokenA, "Comida");
        createRule(tokenA, "MERCADONA", comida);

        mockMvc.perform(get("/api/category-rules").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
