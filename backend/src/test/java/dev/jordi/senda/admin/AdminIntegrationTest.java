package dev.jordi.senda.admin;

import com.jayway.jsonpath.JsonPath;
import dev.jordi.senda.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "name": "Persona"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String registerAdmin(String email) throws Exception {
        String token = register(email);
        grantAdmin(email);
        return token;
    }

    // The test shares one persistence context with the request, so the SQL grant is
    // invisible until the cached entities are dropped. Production has no such overlap.
    private void grantAdmin(String email) {
        entityManager.flush();
        jdbc.update("update users set admin = true where email = ?", email);
        entityManager.clear();
    }

    private long userId(String email) {
        return jdbc.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token, long id, String password)
            throws Exception {
        return mockMvc.perform(post("/api/admin/users/{id}/password", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"newPassword": "%s"}
                        """.formatted(password)));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    @Test
    void adminSeesAccountsWithoutSecrets() throws Exception {
        String admin = registerAdmin("admin@test.dev");
        register("alguien@test.dev");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email", hasItem("alguien@test.dev")))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("passwordHash"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("tokenVersion"))));
    }

    @Test
    void ordinaryUserCannotUseTheAdminApi() throws Exception {
        String user = register("normal@test.dev");
        register("victima@test.dev");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
        reset(user, userId("victima@test.dev"), "nuevaclave123").andExpect(status().isForbidden());
        login("victima@test.dev", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void anonymousCannotUseTheAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void personalTokenOfAnAdminCannotUseTheAdminApi() throws Exception {
        String admin = registerAdmin("admin-token@test.dev");
        String body = mockMvc.perform(post("/api/tokens")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "atajo"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String personal = JsonPath.read(body, "$.value");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + personal))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminResetsPasswordAndClosesTheirSessions() throws Exception {
        String admin = registerAdmin("admin-reset@test.dev");
        String oldSession = register("olvido@test.dev");

        reset(admin, userId("olvido@test.dev"), "nuevaclave123").andExpect(status().isNoContent());

        login("olvido@test.dev", PASSWORD).andExpect(status().isUnauthorized());
        login("olvido@test.dev", "nuevaclave123").andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + oldSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotResetTheirOwnPasswordHere() throws Exception {
        String admin = registerAdmin("admin-self@test.dev");

        reset(admin, userId("admin-self@test.dev"), "nuevaclave123").andExpect(status().isUnprocessableEntity());
        login("admin-self@test.dev", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void resetValidatesTargetAndPassword() throws Exception {
        String admin = registerAdmin("admin-val@test.dev");
        register("x@test.dev");
        long target = userId("x@test.dev");

        reset(admin, target, "corta").andExpect(status().isBadRequest());
        reset(admin, -1, "nuevaclave123").andExpect(status().isNotFound());
    }

    @Test
    void meTellsWhetherTheUserIsAdmin() throws Exception {
        String user = register("me@test.dev");
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@test.dev"))
                .andExpect(jsonPath("$.admin").value(false));

        grantAdmin("me@test.dev");
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.admin").value(true));
    }
}
