package dev.jordi.senda.common;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthContractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithGarbageTokenReturns401() throws Exception {
        mvc.perform(get("/api/transactions").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerThenAccessWithReturnedTokenSucceeds() throws Exception {
        String token = registerAndGetToken("contract-a@test.dev");
        mvc.perform(get("/api/transactions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /** Registers a user and returns its JWT. Reused by the pattern in later tasks. */
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
