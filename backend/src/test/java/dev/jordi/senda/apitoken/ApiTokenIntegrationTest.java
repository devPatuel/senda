package dev.jordi.senda.apitoken;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordi.senda.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ApiTokenIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    @Test
    void createListRevokeFlow() throws Exception {
        String token = registerAndGetToken("tok-a@test.dev");

        // Create -> 201 with the clear value
        String created = mvc.perform(post("/api/tokens")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"iPhone\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value", startsWith("senda_pat_")))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(created).get("id").asLong();

        // List -> metadata, WITHOUT the value
        mvc.perform(get("/api/tokens").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("iPhone"))
                .andExpect(jsonPath("$[0].value").doesNotExist());

        // Revoke -> 204
        mvc.perform(delete("/api/tokens/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void userBCannotRevokeUserAToken() throws Exception {
        String tokenA = registerAndGetToken("tok-owner@test.dev");
        String tokenB = registerAndGetToken("tok-intruder@test.dev");
        String created = mvc.perform(post("/api/tokens")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"iPhone\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(created).get("id").asLong();

        mvc.perform(delete("/api/tokens/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
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
