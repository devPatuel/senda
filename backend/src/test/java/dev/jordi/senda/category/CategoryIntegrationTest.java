package dev.jordi.senda.category;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordi.senda.TestcontainersConfiguration;
import dev.jordi.senda.common.TransactionType;
import dev.jordi.senda.transaction.Transaction;
import dev.jordi.senda.transaction.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CategoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private String tokenA;
    private String tokenB;
    private Long userAId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode userA = register("usera@example.com", "User A");
        JsonNode userB = register("userb@example.com", "User B");
        tokenA = "Bearer " + userA.get("token").asText();
        tokenB = "Bearer " + userB.get("token").asText();
        userAId = userA.get("user").get("id").asLong();
    }

    private JsonNode register(String email, String name) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "password123", "name": "%s"}
                                """.formatted(email, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private Long createCategoryForUserA(String name) throws Exception {
        String body = mockMvc.perform(post("/api/categories")
                        .header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "EXPENSE", "color": "#FF8800"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void createCategoryPersistsAndIsListedOnlyForOwner() throws Exception {
        createCategoryForUserA("Gimnasio");

        mockMvc.perform(get("/api/categories").header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Gimnasio')]").exists());

        // User B has its own 9 default categories, never user A's custom one
        mockMvc.perform(get("/api/categories").header("Authorization", tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[?(@.name == 'Gimnasio')]").doesNotExist());
    }

    @Test
    void userBCannotAssignOrTargetCategoryOfUserA() throws Exception {
        Long categoryId = createCategoryForUserA("Gimnasio");

        mockMvc.perform(post("/api/categories/" + categoryId + "/assign")
                        .header("Authorization", tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 100.00}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/categories/" + categoryId + "/target")
                        .header("Authorization", tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAmount": 200.00}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void userBCannotUpdateCategoryOfUserA() throws Exception {
        Long categoryId = createCategoryForUserA("Gimnasio");

        mockMvc.perform(put("/api/categories/" + categoryId)
                        .header("Authorization", tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Hackeada", "color": "#000000"}
                                """))
                .andExpect(status().isNotFound());

        Category category = categoryRepository.findById(categoryId).orElseThrow();
        assertThat(category.getName()).isEqualTo("Gimnasio");
    }

    @Test
    void userBCannotDeleteCategoryOfUserA() throws Exception {
        Long categoryId = createCategoryForUserA("Gimnasio");

        mockMvc.perform(delete("/api/categories/" + categoryId)
                        .header("Authorization", tokenB))
                .andExpect(status().isNotFound());

        assertThat(categoryRepository.findById(categoryId)).isPresent();
    }

    @Test
    void deleteCategoryWithoutTransactionsRemovesItPhysically() throws Exception {
        Long categoryId = createCategoryForUserA("Gimnasio");

        mockMvc.perform(delete("/api/categories/" + categoryId)
                        .header("Authorization", tokenA))
                .andExpect(status().isNoContent());

        assertThat(categoryRepository.findById(categoryId)).isEmpty();
    }

    @Test
    void deleteCategoryWithTransactionsDeactivatesItInDatabase() throws Exception {
        Long categoryId = createCategoryForUserA("Gimnasio");
        Category category = categoryRepository.findById(categoryId).orElseThrow();
        transactionRepository.save(new Transaction(userAId, category, TransactionType.EXPENSE,
                new BigDecimal("25.00"), LocalDate.of(2026, 6, 1), "Mensualidad"));

        mockMvc.perform(delete("/api/categories/" + categoryId)
                        .header("Authorization", tokenA))
                .andExpect(status().isNoContent());

        Category persisted = categoryRepository.findById(categoryId).orElseThrow();
        assertThat(persisted.isActive()).isFalse();

        // Hidden from the default listing, visible with includeInactive=true
        mockMvc.perform(get("/api/categories").header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Gimnasio')]").doesNotExist());
        mockMvc.perform(get("/api/categories")
                        .param("includeInactive", "true")
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Gimnasio')]").exists());
    }

    @Test
    void duplicateNameForSameUserAndTypeReturns409() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Comida", "type": "EXPENSE", "color": "#FF8800"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
