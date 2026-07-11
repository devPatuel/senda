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

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TokenAuthIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    @Test
    void personalTokenAuthenticatesProtectedRequest() throws Exception {
        String jwt = registerAndGetToken("pat-a@test.dev");
        String pat = createPersonalToken(jwt);

        // Use the personal token on a normal endpoint -> 200
        mvc.perform(get("/api/transactions").header("Authorization", "Bearer " + pat))
                .andExpect(status().isOk());
    }

    @Test
    void revokedPersonalTokenIsRejected() throws Exception {
        String jwt = registerAndGetToken("pat-b@test.dev");
        String created = mvc.perform(post("/api/tokens")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"iPhone\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(created).get("id").asLong();
        String pat = mapper.readTree(created).get("value").asText();
        mvc.perform(delete("/api/tokens/" + id).header("Authorization", "Bearer " + jwt));

        mvc.perform(get("/api/transactions").header("Authorization", "Bearer " + pat))
                .andExpect(status().isUnauthorized());
    }

    private String createPersonalToken(String jwt) throws Exception {
        String created = mvc.perform(post("/api/tokens")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"iPhone\"}"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(created).get("value").asText();
    }

    private String registerAndGetToken(String email) throws Exception {
        String body = """
                {"name":"Test","email":"%s","password":"password123"}
                """.formatted(email);
        String response = mvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("token").asText();
    }
}
