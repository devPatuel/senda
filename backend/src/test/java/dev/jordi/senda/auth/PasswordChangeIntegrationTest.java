package dev.jordi.senda.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordi.senda.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PasswordChangeIntegrationTest {

    private static final String OLD = "password123";
    private static final String NEW = "a-better-password";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    @Test
    void changingThePasswordClosesEarlierSessionsAndKeepsThisOne() throws Exception {
        String oldSession = register("pwd-a@test.dev");

        String body = changePassword(oldSession, OLD, NEW)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newSession = mapper.readTree(body).get("token").asText();

        // A session stolen before the change is useless from now on
        mvc.perform(get("/api/categories").header("Authorization", "Bearer " + oldSession))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/categories").header("Authorization", "Bearer " + newSession))
                .andExpect(status().isOk());

        login("pwd-a@test.dev", OLD).andExpect(status().isUnauthorized());
        login("pwd-a@test.dev", NEW).andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordChangesNothing() throws Exception {
        String session = register("pwd-b@test.dev");

        // 422 and not 401: the web client reads a 401 as "session expired" and logs out
        changePassword(session, "not-my-password", NEW).andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/categories").header("Authorization", "Bearer " + session))
                .andExpect(status().isOk());
        login("pwd-b@test.dev", OLD).andExpect(status().isOk());
    }

    @Test
    void newPasswordMustMeetTheSignUpRules() throws Exception {
        String session = register("pwd-c@test.dev");

        changePassword(session, OLD, "short").andExpect(status().isBadRequest());
    }

    @Test
    void changingThePasswordRequiresASession() throws Exception {
        mvc.perform(post("/api/auth/password")
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + OLD + "\",\"newPassword\":\"" + NEW + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions changePassword(String session, String current, String next) throws Exception {
        return mvc.perform(post("/api/auth/password")
                .header("Authorization", "Bearer " + session)
                .contentType(APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String register(String email) throws Exception {
        String response = mvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"email\":\"" + email + "\",\"password\":\"" + OLD + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("token").asText();
    }
}
