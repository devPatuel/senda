package dev.jordi.senda.space;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Domains without a {@code space_id} column (debt, recurring, investment, ...)
 * must ignore a {@code ?spaceId=} query param: it is inert and can never widen
 * the personal scope to another user's or space's resources.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PersonalModulesIgnoreSpaceIntegrationTest {

    @Autowired
    MockMvc mockMvc;

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

    private void createDebt(String token, String counterparty) throws Exception {
        mockMvc.perform(post("/api/debts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction": "I_OWE", "counterparty": "%s", "concept": "x",
                                 "originalAmount": 100.00, "date": "2026-06-01"}
                                """.formatted(counterparty)))
                .andExpect(status().isCreated());
    }

    @Test
    void debtsListIgnoresUnexpectedSpaceIdParam() throws Exception {
        String tokenA = register("psx-a@example.com", "A");
        String tokenB = register("psx-b@example.com", "B");
        long spaceB = createSpace(tokenB, "Casa B");
        createDebt(tokenB, "Banco de B");

        // A has no debts. Slipping B's spaceId into the query must not surface B's debt.
        mockMvc.perform(get("/api/debts?spaceId=" + spaceB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
