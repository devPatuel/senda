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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void personalTokenCanCaptureAnExpense() throws Exception {
        String jwt = registerAndGetToken("pat-a@test.dev");
        String pat = createPersonalToken(jwt);

        // What a shortcut needs: read the categories and create the movement
        String categories = mvc.perform(get("/api/categories").param("type", "EXPENSE")
                        .header("Authorization", "Bearer " + pat))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long categoryId = mapper.readTree(categories).get(0).get("id").asLong();

        mvc.perform(post("/api/transactions/quick")
                        .header("Authorization", "Bearer " + pat)
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":3.20,\"description\":\"Cafe\",\"categoryId\":" + categoryId + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    void personalTokenCannotReadOrManageAnythingElse() throws Exception {
        String jwt = registerAndGetToken("pat-c@test.dev");
        String pat = createPersonalToken(jwt);

        // A token that leaks from a phone must not hand over the finances...
        mvc.perform(get("/api/transactions").header("Authorization", "Bearer " + pat))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/networth").header("Authorization", "Bearer " + pat))
                .andExpect(status().isForbidden());
        // ...nor mint more tokens or change the password
        mvc.perform(post("/api/tokens")
                        .header("Authorization", "Bearer " + pat)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"otro\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/password")
                        .header("Authorization", "Bearer " + pat)
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"another-pass-1\"}"))
                .andExpect(status().isForbidden());
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

        mvc.perform(get("/api/categories").header("Authorization", "Bearer " + pat))
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

    // -------------------------------------------------------------------------
    // ASSISTANT scope and the author of each movement
    // -------------------------------------------------------------------------

    private record CreatedToken(long id, String value) {
    }

    private CreatedToken createToken(String jwt, String name, String scope) throws Exception {
        String created = mvc.perform(post("/api/tokens")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"scope\":\"" + scope + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new CreatedToken(mapper.readTree(created).get("id").asLong(),
                mapper.readTree(created).get("value").asText());
    }

    private long expenseCategory(String jwt) throws Exception {
        String categories = mvc.perform(get("/api/categories").param("type", "EXPENSE")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(categories).get(0).get("id").asLong();
    }

    private String movementJson(long categoryId, String extra) {
        return "{\"categoryId\":" + categoryId + ",\"type\":\"EXPENSE\",\"amount\":8.26,"
                + "\"date\":\"2026-06-10\"" + extra + "}";
    }

    private long createMovement(String credential, long categoryId) throws Exception {
        String body = mvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + credential)
                        .contentType(APPLICATION_JSON).content(movementJson(categoryId, "")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asLong();
    }

    private com.fasterxml.jackson.databind.JsonNode readMovement(String jwt, long id) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/transactions/" + id)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void assistantToken_canRead() throws Exception {
        String jwt = registerAndGetToken("assistant-read@test.dev");
        String bot = createToken(jwt, "Bot", "ASSISTANT").value();

        for (String url : java.util.List.of("/api/transactions/summary?year=2026&month=6", "/api/categories",
                "/api/categories/budget", "/api/accounts", "/api/spaces")) {
            mvc.perform(get(url).header("Authorization", "Bearer " + bot)).andExpect(status().isOk());
        }
    }

    @Test
    void assistantToken_canCreateMovement() throws Exception {
        String jwt = registerAndGetToken("assistant-create@test.dev");
        String bot = createToken(jwt, "Bot", "ASSISTANT").value();

        createMovement(bot, expenseCategory(jwt));
    }

    @Test
    void assistantToken_cannotWriteElse() throws Exception {
        String jwt = registerAndGetToken("assistant-write@test.dev");
        String bot = createToken(jwt, "Bot", "ASSISTANT").value();
        long category = expenseCategory(jwt);
        long movement = createMovement(jwt, category);
        String auth = "Bearer " + bot;

        mvc.perform(put("/api/transactions/" + movement).header("Authorization", auth)
                .contentType(APPLICATION_JSON).content(movementJson(category, "")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/transactions/" + movement).header("Authorization", auth))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/accounts/1").header("Authorization", auth)
                .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/accounts/baseline").header("Authorization", auth))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tokens").header("Authorization", auth)
                .contentType(APPLICATION_JSON).content("{\"name\":\"otro\",\"scope\":\"ASSISTANT\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/tokens").header("Authorization", auth))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", auth))
                .andExpect(status().isForbidden());
        // Not on the list either: reading every movement is more than the bot needs
        mvc.perform(get("/api/transactions").header("Authorization", auth))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/networth").header("Authorization", auth))
                .andExpect(status().isForbidden());
    }

    @Test
    void quickToken_keepsOnlyCurrentAccess() throws Exception {
        String jwt = registerAndGetToken("quick-scope@test.dev");
        String pat = createPersonalToken(jwt);

        mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + pat))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/transactions").header("Authorization", "Bearer " + pat)
                .contentType(APPLICATION_JSON).content(movementJson(expenseCategory(jwt), "")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createdVia_isTokenName_andNullFromWeb() throws Exception {
        String jwt = registerAndGetToken("via-name@test.dev");
        String bot = createToken(jwt, "Bot Telegram", "ASSISTANT").value();
        long category = expenseCategory(jwt);

        long fromBot = createMovement(bot, category);
        long fromWeb = createMovement(jwt, category);

        org.assertj.core.api.Assertions.assertThat(readMovement(jwt, fromBot).get("createdVia").asText())
                .isEqualTo("Bot Telegram");
        org.assertj.core.api.Assertions.assertThat(readMovement(jwt, fromWeb).get("createdVia").isNull()).isTrue();
    }

    @Test
    void createdVia_isTheTokenNameOnQuickCapture() throws Exception {
        String jwt = registerAndGetToken("via-quick@test.dev");
        String pat = createPersonalToken(jwt);

        String body = mvc.perform(post("/api/transactions/quick")
                        .header("Authorization", "Bearer " + pat)
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":3.20,\"description\":\"Cafe\",\"categoryId\":" + expenseCategory(jwt) + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(mapper.readTree(body).get("createdVia").asText())
                .isEqualTo("iPhone");
    }

    @Test
    void createdVia_ignoredInRequestBody() throws Exception {
        String jwt = registerAndGetToken("via-forged@test.dev");
        String body = mvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(APPLICATION_JSON)
                        .content(movementJson(expenseCategory(jwt), ",\"createdVia\":\"Otro\"")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(mapper.readTree(body).get("createdVia").isNull()).isTrue();
    }

    @Test
    void createdVia_survivesTokenRevocation() throws Exception {
        String jwt = registerAndGetToken("via-revoked@test.dev");
        CreatedToken bot = createToken(jwt, "Bot", "ASSISTANT");
        long movement = createMovement(bot.value(), expenseCategory(jwt));

        mvc.perform(delete("/api/tokens/" + bot.id()).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(readMovement(jwt, movement).get("createdVia").asText())
                .isEqualTo("Bot");
    }

    @Test
    void filterByCreatedVia() throws Exception {
        String jwt = registerAndGetToken("via-filter@test.dev");
        String bot = createToken(jwt, "Bot", "ASSISTANT").value();
        long category = expenseCategory(jwt);
        long fromBot = createMovement(bot, category);
        createMovement(jwt, category);

        var page = mapper.readTree(mvc.perform(get("/api/transactions").param("createdVia", "Bot")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        org.assertj.core.api.Assertions.assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(page.get("content").get(0).get("id").asLong()).isEqualTo(fromBot);
    }

    @Test
    void revokedAssistantToken_unauthorized() throws Exception {
        String jwt = registerAndGetToken("assistant-revoked@test.dev");
        CreatedToken bot = createToken(jwt, "Bot", "ASSISTANT");
        mvc.perform(delete("/api/tokens/" + bot.id()).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + bot.value()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutScopeStaysQuick() throws Exception {
        String jwt = registerAndGetToken("default-scope@test.dev");
        createPersonalToken(jwt);

        var tokens = mapper.readTree(mvc.perform(get("/api/tokens").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        org.assertj.core.api.Assertions.assertThat(tokens.get(0).get("scope").asText()).isEqualTo("QUICK");
    }
}
