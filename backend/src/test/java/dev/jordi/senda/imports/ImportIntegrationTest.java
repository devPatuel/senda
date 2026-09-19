package dev.jordi.senda.imports;

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

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String token;
    private long comida;
    private long nomina;
    private final String today = LocalDate.now().toString();

    @BeforeEach
    void setUp() throws Exception {
        token = register("import-user@example.com", "Import User");
        comida = categoryId("EXPENSE", "Comida");
        nomina = categoryId("INCOME", "Nómina");
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

    private long categoryId(String type, String name) throws Exception {
        String body = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .param("type", type))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name=='" + name + "')].id");
        return ids.get(0).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions preview(String json) throws Exception {
        return mockMvc.perform(post("/api/imports/preview")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private org.springframework.test.web.servlet.ResultActions commit(String json) throws Exception {
        return mockMvc.perform(post("/api/imports/commit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // Cross-tenant: user B cannot commit an import that references user A's category.
    @Test
    void commitCannotUseAnotherUsersCategory() throws Exception {
        String tokenB = register("import-intruder@example.com", "Intruder");
        String body = """
                {"rows": [
                  {"date": "%s", "description": "x", "amount": 10.00, "type": "EXPENSE", "categoryId": %d}
                ]}
                """.formatted(today, comida); // 'comida' belongs to user A

        mockMvc.perform(post("/api/imports/commit")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void previewDerivesTypeAndFlagsDuplicates() throws Exception {
        preview("""
                {"rows": [
                  {"date": "%s", "description": "Compra MERCADONA 23", "amount": -20.50},
                  {"date": "%s", "description": "Nomina mayo", "amount": 1500.00}
                ]}
                """.formatted(today, today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$[0].amount").value(20.50))
                .andExpect(jsonPath("$[0].duplicate").value(false))
                .andExpect(jsonPath("$[1].type").value("INCOME"))
                .andExpect(jsonPath("$[1].amount").value(1500.00));
    }

    @Test
    void commitImportsAndThenSkipsDuplicates() throws Exception {
        String body = """
                {"rows": [
                  {"date": "%s", "description": "Compra", "amount": 10.00, "type": "EXPENSE", "categoryId": %d}
                ]}
                """.formatted(today, comida);

        commit(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.skipped").value(0));

        // Re-importing the same row is detected as a duplicate
        commit(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void commitCollapsesDuplicateRowsWithinTheSameBatch() throws Exception {
        commit("""
                {"rows": [
                  {"date": "%s", "description": "Cafe", "amount": 2.50, "type": "EXPENSE", "categoryId": %d},
                  {"date": "%s", "description": "Cafe", "amount": 2.50, "type": "EXPENSE", "categoryId": %d}
                ]}
                """.formatted(today, comida, today, comida))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void commitRejectsTypeNotMatchingCategory() throws Exception {
        // EXPENSE row pointing at an INCOME category
        commit("""
                {"rows": [
                  {"date": "%s", "description": "X", "amount": 10.00, "type": "EXPENSE", "categoryId": %d}
                ]}
                """.formatted(today, nomina))
                .andExpect(status().isBadRequest());
    }

    // --- Space-scoped import (shared account statements) ---

    private long createSpace(String name) throws Exception {
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

    private long createSpaceCategory(long spaceId, String type, String name) throws Exception {
        String body = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "%s", "color": "#10b981", "spaceId": %d}
                                """.formatted(name, type, spaceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void commitIntoSpace_createsTransactionsInThatSpace() throws Exception {
        long spaceId = createSpace("Pareja");
        long superCat = createSpaceCategory(spaceId, "EXPENSE", "Supermercados");

        commit("""
                {"spaceId": %d, "rows": [
                  {"date": "%s", "description": "Mercadona", "amount": 73.15, "type": "EXPENSE", "categoryId": %d}
                ]}
                """.formatted(spaceId, today, superCat))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));

        // It belongs to the space...
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("spaceId", String.valueOf(spaceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Mercadona"));

        // ...and never leaks into the personal ledger
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void commitIntoSpaceRejectsPersonalCategory() throws Exception {
        long spaceId = createSpace("Pareja");

        // A personal category is out of scope for a space import: 404, not a
        // silently mis-scoped transaction.
        commit("""
                {"spaceId": %d, "rows": [
                  {"date": "%s", "description": "Mercadona", "amount": 10.00, "type": "EXPENSE", "categoryId": %d}
                ]}
                """.formatted(spaceId, today, comida))
                .andExpect(status().isNotFound());
    }

    @Test
    void commitIntoSpaceRejectsNonMember() throws Exception {
        long spaceId = createSpace("Pareja");
        long superCat = createSpaceCategory(spaceId, "EXPENSE", "Supermercados");

        String outsider = register("outsider@example.com", "Outsider");
        mockMvc.perform(post("/api/imports/commit")
                        .header("Authorization", "Bearer " + outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spaceId": %d, "rows": [
                                  {"date": "%s", "description": "Mercadona", "amount": 10.00, "type": "EXPENSE", "categoryId": %d}
                                ]}
                                """.formatted(spaceId, today, superCat)))
                .andExpect(status().isNotFound());
    }

    @Test
    void previewIntoSpace_flagsDuplicatesWithinTheSpaceOnly() throws Exception {
        long spaceId = createSpace("Pareja");
        long superCat = createSpaceCategory(spaceId, "EXPENSE", "Supermercados");

        commit("""
                {"spaceId": %d, "rows": [
                  {"date": "%s", "description": "Mercadona", "amount": 73.15, "type": "EXPENSE", "categoryId": %d}
                ]}
                """.formatted(spaceId, today, superCat))
                .andExpect(status().isOk());

        // Same row previewed against the space: already there
        preview("""
                {"spaceId": %d, "rows": [
                  {"date": "%s", "description": "Mercadona", "amount": -73.15}
                ]}
                """.formatted(spaceId, today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].duplicate").value(true));

        // Same row previewed personally: the personal ledger does not have it
        preview("""
                {"rows": [
                  {"date": "%s", "description": "Mercadona", "amount": -73.15}
                ]}
                """.formatted(today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].duplicate").value(false));
    }
}
