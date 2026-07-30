package dev.jordi.senda.common;

import dev.jordi.senda.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The allowed origins must come from configuration, not from a hardcoded list.
 * Production serves the SPA and the API from the same origin behind a reverse
 * proxy, but browsers still send an Origin header on same-origin POSTs, so a
 * list frozen at the development URL rejects every write with 403.
 */
@SpringBootTest(properties = "senda.cors.allowed-origins=https://servidor.ejemplo.ts.net:9446")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String REGISTER_BODY = """
            {"email": "cors@example.com", "password": "password123", "name": "Cors"}
            """;

    @Test
    void acceptsWriteFromConfiguredOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "https://servidor.ejemplo.ts.net:9446")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsWriteFromUnknownOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "https://atacante.example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isForbidden());
    }
}
