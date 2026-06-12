package dev.jordi.senda.auth;

import dev.jordi.senda.TestcontainersConfiguration;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    private static final String REGISTER_BODY = """
            {"email": "jordi@example.com", "password": "password123", "name": "Jordi"}
            """;

    @Test
    void registerPersistsUserAndCopiesDefaultCategories() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("jordi@example.com"));

        var user = userRepository.findByEmail("jordi@example.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("password123");
        assertThat(categoryRepository.findByUserId(user.getId())).hasSize(9);
    }

    @Test
    void registerWithDuplicateEmailReturns409() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void registerThenLoginEndToEnd() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "jordi@example.com", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.id").isNumber());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "jordi@example.com", "password": "wrongpass1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401InJson() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void protectedEndpointWithInvalidTokenReturns401InJson() throws Exception {
        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
