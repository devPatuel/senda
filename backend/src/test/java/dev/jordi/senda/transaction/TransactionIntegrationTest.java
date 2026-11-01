package dev.jordi.senda.transaction;

import com.jayway.jsonpath.JsonPath;
import dev.jordi.senda.TestcontainersConfiguration;
import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.common.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
class TransactionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CategoryRepository categoryRepository;

    private String tokenA;
    private String tokenB;
    private Category expenseA;
    private Category expenseA2;
    private Category incomeA;
    private Category expenseB;

    @BeforeEach
    void registerUsers() throws Exception {
        var registrationA = register("usera@example.com", "User A");
        tokenA = registrationA.token();
        var registrationB = register("userb@example.com", "User B");
        tokenB = registrationB.token();

        var categoriesA = categoryRepository.findByUserIdAndSpaceIdIsNullAndActiveTrue(registrationA.userId());
        expenseA = categoriesA.stream().filter(c -> c.getType() == TransactionType.EXPENSE)
                .findFirst().orElseThrow();
        expenseA2 = categoriesA.stream().filter(c -> c.getType() == TransactionType.EXPENSE)
                .filter(c -> !c.getId().equals(expenseA.getId())).findFirst().orElseThrow();
        incomeA = categoriesA.stream().filter(c -> c.getType() == TransactionType.INCOME)
                .findFirst().orElseThrow();
        expenseB = categoryRepository.findByUserIdAndSpaceIdIsNullAndActiveTrue(registrationB.userId()).stream()
                .filter(c -> c.getType() == TransactionType.EXPENSE).findFirst().orElseThrow();
    }

    private record Registration(String token, Long userId) {
    }

    private Registration register(String email, String name) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "password123", "name": "%s"}
                                """.formatted(email, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Registration(JsonPath.read(body, "$.token"),
                ((Number) JsonPath.read(body, "$.user.id")).longValue());
    }

    private long createTransaction(String token, Long categoryId, TransactionType type,
                                   String amount, String date) throws Exception {
        String body = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "%s", "amount": %s, "date": "%s"}
                                """.formatted(categoryId, type, amount, date)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void transactionResponseCarriesCategoryEmoji() throws Exception {
        expenseA.setEmoji("🍔");
        categoryRepository.save(expenseA);

        long txId = createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE,
                "12.50", "2026-06-10");

        mockMvc.perform(get("/api/transactions/" + txId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryEmoji").value("🍔"));
    }

    // --- KEY TEST: user isolation ---

    @Test
    void userBCannotSeeOrTouchUserAData() throws Exception {
        long txId = createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE,
                "100.50", "2026-06-05");
        createTransaction(tokenA, incomeA.getId(), TransactionType.INCOME, "1500.00", "2026-06-01");

        // B's listing does not include A's transactions
        mockMvc.perform(get("/api/transactions").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        // GET / PUT / DELETE on A's transaction id return 404 for B (not 403)
        mockMvc.perform(get("/api/transactions/" + txId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/transactions/" + txId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "EXPENSE", "amount": 1.00, "date": "2026-06-05"}
                                """.formatted(expenseB.getId())))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/transactions/" + txId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // B's summary does not include A's data
        mockMvc.perform(get("/api/transactions/summary")
                        .header("Authorization", "Bearer " + tokenB)
                        .param("year", "2026").param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(0))
                .andExpect(jsonPath("$.totalExpense").value(0))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.byCategory").isEmpty());

        // A still sees its transaction intact after B's attempts
        mockMvc.perform(get("/api/transactions/" + txId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100.50));
    }

    @Test
    void creatingTransactionWithAnotherUsersCategoryReturns404() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "EXPENSE", "amount": 5.00, "date": "2026-06-05"}
                                """.formatted(expenseA.getId())))
                .andExpect(status().isNotFound());
    }

    // --- CRUD round-trip ---

    @Test
    void crudRoundTrip() throws Exception {
        long txId = createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE,
                "12.50", "2026-06-10");

        mockMvc.perform(get("/api/transactions/" + txId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(expenseA.getId()))
                .andExpect(jsonPath("$.categoryName").value(expenseA.getName()))
                .andExpect(jsonPath("$.categoryColor").value(expenseA.getColor()))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.amount").value(12.50))
                .andExpect(jsonPath("$.date").value("2026-06-10"))
                .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // Update switches to another category of the same user
        mockMvc.perform(put("/api/transactions/" + txId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "EXPENSE", "amount": 20.00, "date": "2026-06-11", "description": "Updated"}
                                """.formatted(expenseA2.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(expenseA2.getId()))
                .andExpect(jsonPath("$.amount").value(20.00))
                .andExpect(jsonPath("$.date").value("2026-06-11"))
                .andExpect(jsonPath("$.description").value("Updated"));

        mockMvc.perform(delete("/api/transactions/" + txId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/transactions/" + txId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void typeMismatchWithCategoryReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "INCOME", "amount": 5.00, "date": "2026-06-05"}
                                """.formatted(expenseA.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Transaction type INCOME does not match category type EXPENSE"));
    }

    @Test
    void inactiveCategoryReturns409() throws Exception {
        expenseA.setActive(false);
        categoryRepository.saveAndFlush(expenseA);

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "EXPENSE", "amount": 5.00, "date": "2026-06-05"}
                                """.formatted(expenseA.getId())))
                .andExpect(status().isConflict());
    }

    // --- listing: filters, pagination, ordering ---

    @Test
    void listFiltersByDateRangeCategoryAndType() throws Exception {
        createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE, "10.00", "2026-05-31");
        createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE, "20.00", "2026-06-10");
        createTransaction(tokenA, expenseA2.getId(), TransactionType.EXPENSE, "30.00", "2026-06-15");
        createTransaction(tokenA, incomeA.getId(), TransactionType.INCOME, "1500.00", "2026-06-01");

        // Date range covers June only
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("from", "2026-06-01").param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        // Category filter
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("categoryId", String.valueOf(expenseA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Type filter
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("type", "INCOME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(1500.00));

        // Combined filters
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("from", "2026-06-01").param("to", "2026-06-30")
                        .param("categoryId", String.valueOf(expenseA.getId()))
                        .param("type", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(20.00));
    }

    @Test
    void listPaginatesAndOrdersByDateDescThenIdDesc() throws Exception {
        long first = createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE, "1.00", "2026-06-01");
        long second = createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE, "2.00", "2026-06-03");
        // Same date as `second` but inserted later: higher id wins the tie
        long third = createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE, "3.00", "2026-06-03");

        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void listWithSizeOver100Returns400() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    // --- year summary ---

    @Test
    void yearSummaryReportsTransfersInApartFromSpending() throws Exception {
        // Money moved into the account, not earned: the couple's contributions
        incomeA.setTransfer(true);
        categoryRepository.saveAndFlush(incomeA);

        createTransaction(tokenA, incomeA.getId(), TransactionType.INCOME, "800.00", "2026-03-01");
        createTransaction(tokenA, incomeA.getId(), TransactionType.INCOME, "700.00", "2026-04-01");
        createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE, "100.00", "2026-03-15");

        mockMvc.perform(get("/api/transactions/summary/year")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransfersIn").value(1500.00))
                // A transfer is money moved in, never spending
                .andExpect(jsonPath("$.totalExpense").value(100.00))
                .andExpect(jsonPath("$.months[2].transfersIn").value(800.00))
                .andExpect(jsonPath("$.months[2].expense").value(100.00))
                .andExpect(jsonPath("$.months[3].transfersIn").value(700.00))
                .andExpect(jsonPath("$.months[0].transfersIn").value(0));
    }

    @Test
    void yearSummaryAggregatesTheWholeYearMonthByMonth() throws Exception {
        createTransaction(tokenA, incomeA.getId(), TransactionType.INCOME, "1500.00", "2026-01-31");
        createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE, "100.00", "2026-01-15");
        createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE, "50.00", "2026-06-10");
        createTransaction(tokenA, expenseA2.getId(), TransactionType.EXPENSE, "25.50", "2026-12-31");
        // Other years and other users must not leak in
        createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE, "999.99", "2025-12-31");
        createTransaction(tokenB, expenseB.getId(), TransactionType.EXPENSE, "77.77", "2026-06-15");

        mockMvc.perform(get("/api/transactions/summary/year")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.totalExpense").value(175.50))
                // Always twelve rows, so the chart never has to guess at gaps
                .andExpect(jsonPath("$.months.length()").value(12))
                .andExpect(jsonPath("$.months[0].expense").value(100.00))
                .andExpect(jsonPath("$.months[1].expense").value(0))
                .andExpect(jsonPath("$.months[5].expense").value(50.00))
                .andExpect(jsonPath("$.months[11].expense").value(25.50));
    }

    @Test
    void yearSummaryWithInvalidYearReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions/summary/year")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("year", "0"))
                .andExpect(status().isBadRequest());
    }

    // --- summary ---

    @Test
    void summaryAggregatesMonthWithExactDecimals() throws Exception {
        createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE, "10.25", "2026-06-05");
        createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE, "5.50", "2026-06-20");
        createTransaction(tokenA, expenseA2.getId(), TransactionType.EXPENSE, "20.00", "2026-06-30");
        createTransaction(tokenA, incomeA.getId(), TransactionType.INCOME, "1500.00", "2026-06-01");
        // Outside the requested month: must be excluded
        createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE, "999.99", "2026-05-31");
        createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE, "999.99", "2026-07-01");
        // Other user's data: must be excluded
        createTransaction(tokenB, expenseB.getId(), TransactionType.EXPENSE, "77.77", "2026-06-15");

        mockMvc.perform(get("/api/transactions/summary")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("year", "2026").param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.month").value(6))
                .andExpect(jsonPath("$.totalIncome").value(1500.00))
                .andExpect(jsonPath("$.totalExpense").value(35.75))
                .andExpect(jsonPath("$.balance").value(1464.25))
                .andExpect(jsonPath("$.byCategory.length()").value(3))
                .andExpect(jsonPath("$.byCategory[?(@.categoryId == %d)].total"
                        .formatted(expenseA.getId())).value(15.75))
                .andExpect(jsonPath("$.byCategory[?(@.categoryId == %d)].total"
                        .formatted(expenseA2.getId())).value(20.00))
                .andExpect(jsonPath("$.byCategory[?(@.categoryId == %d)].total"
                        .formatted(incomeA.getId())).value(1500.00))
                .andExpect(jsonPath("$.byCategory[?(@.categoryId == %d)].categoryName"
                        .formatted(expenseA.getId())).value(expenseA.getName()))
                .andExpect(jsonPath("$.byCategory[?(@.categoryId == %d)].categoryColor"
                        .formatted(expenseA.getId())).value(expenseA.getColor()));
    }

    @Test
    void summaryWithMissingOrInvalidParamsReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions/summary")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/transactions/summary")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest());
    }

    // --- trends ---

    @Test
    void trendsReturnsDenseSeriesWithCurrentMonthTotals() throws Exception {
        String today = java.time.LocalDate.now().toString();
        createTransaction(tokenA, incomeA.getId(), TransactionType.INCOME, "1000.00", today);
        createTransaction(tokenA, expenseA.getId(), TransactionType.EXPENSE, "300.00", today);

        // months=3 -> exactly 3 ordered rows, the last one is the current month
        mockMvc.perform(get("/api/transactions/trends")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("months", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].income").value(1000.00))
                .andExpect(jsonPath("$[2].expense").value(300.00))
                .andExpect(jsonPath("$[2].balance").value(700.00));
    }

    @Test
    void trendsDefaultsToSixMonthsAndRejectsOutOfRange() throws Exception {
        mockMvc.perform(get("/api/transactions/trends").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));

        mockMvc.perform(get("/api/transactions/trends")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("months", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trendsIsolatesByUser() throws Exception {
        String today = java.time.LocalDate.now().toString();
        createTransaction(tokenA, incomeA.getId(), TransactionType.INCOME, "1000.00", today);

        // B has no transactions: the current month shows zeros
        mockMvc.perform(get("/api/transactions/trends")
                        .header("Authorization", "Bearer " + tokenB)
                        .param("months", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].income").value(0.00));
    }

    // -------------------------------------------------------------------------
    // Movement and balance together
    // -------------------------------------------------------------------------

    private long createAccount(String token, String name, String balance, Long spaceId) throws Exception {
        String space = spaceId == null ? "null" : spaceId.toString();
        String body = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "BANK", "balance": %s, "currency": "EUR", "spaceId": %s}
                                """.formatted(name, balance, space)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions postWithAccount(String token, Long categoryId, TransactionType type, String amount,
                                          long accountId, Long spaceId) throws Exception {
        String space = spaceId == null ? "null" : spaceId.toString();
        return mockMvc.perform(post("/api/transactions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoryId": %d, "type": "%s", "amount": %s, "date": "2026-06-10",
                         "accountId": %d, "spaceId": %s}
                        """.formatted(categoryId, type, amount, accountId, space)));
    }

    private long expenseWithAccount(long accountId, String amount) throws Exception {
        String body = postWithAccount(tokenA, expenseA.getId(), TransactionType.EXPENSE, amount, accountId, null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions putMovement(long id, Long categoryId, TransactionType type, String amount) throws Exception {
        return mockMvc.perform(put("/api/transactions/" + id)
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoryId": %d, "type": "%s", "amount": %s, "date": "2026-06-10"}
                        """.formatted(categoryId, type, amount)));
    }

    private double balance(String token, long accountId, Long spaceId) throws Exception {
        String url = "/api/accounts?includeArchived=true" + (spaceId == null ? "" : "&spaceId=" + spaceId);
        String body = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> balances = JsonPath.read(body, "$[?(@.id==" + accountId + ")].balance");
        return balances.get(0).doubleValue();
    }

    private int movementCount(String token, Long spaceId) throws Exception {
        String url = "/api/transactions" + (spaceId == null ? "" : "?spaceId=" + spaceId);
        String body = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.totalElements")).intValue();
    }

    private long createSpace(String token) throws Exception {
        String body = mockMvc.perform(post("/api/spaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Pareja\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long spaceCategory(String token, long spaceId, String type) throws Exception {
        String body = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .param("spaceId", String.valueOf(spaceId))
                        .param("type", type))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$[0].id")).longValue();
    }

    private void baseline(String token) throws Exception {
        mockMvc.perform(post("/api/accounts/baseline").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private double discrepancy(String token, Long spaceId) throws Exception {
        String url = "/api/categories/budget" + (spaceId == null ? "" : "?spaceId=" + spaceId);
        String body = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.discrepancy")).doubleValue();
    }

    private void updateAccount(long id, String balance, boolean archived) throws Exception {
        mockMvc.perform(put("/api/accounts/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Banco", "type": "BANK", "balance": %s, "currency": "EUR", "archived": %s}
                                """.formatted(balance, archived)))
                .andExpect(status().isOk());
    }

    @Test
    void createWithAccount_subtractsExpense() throws Exception {
        long account = createAccount(tokenA, "Banco", "308.26", null);

        postWithAccount(tokenA, expenseA.getId(), TransactionType.EXPENSE, "8.26", account, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(account))
                .andExpect(jsonPath("$.accountBalance").value(300.00));

        assertThat(balance(tokenA, account, null)).isEqualTo(300.00);
    }

    @Test
    void createIncomeWithAccount_adds() throws Exception {
        long account = createAccount(tokenA, "Banco", "100.00", null);

        postWithAccount(tokenA, incomeA.getId(), TransactionType.INCOME, "50.00", account, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountBalance").value(150.00));

        assertThat(balance(tokenA, account, null)).isEqualTo(150.00);
    }

    @Test
    void createWithoutAccount_leavesBalance() throws Exception {
        long account = createAccount(tokenA, "Banco", "100.00", null);

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "EXPENSE", "amount": 8.26, "date": "2026-06-10"}
                                """.formatted(expenseA.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").doesNotExist())
                .andExpect(jsonPath("$.accountBalance").doesNotExist());

        assertThat(balance(tokenA, account, null)).isEqualTo(100.00);
    }

    @Test
    void foreignAccount_notFound_nothingSaved() throws Exception {
        long foreign = createAccount(tokenB, "Ajena", "100.00", null);

        postWithAccount(tokenA, expenseA.getId(), TransactionType.EXPENSE, "8.26", foreign, null)
                .andExpect(status().isNotFound());

        assertThat(movementCount(tokenA, null)).isZero();
        assertThat(balance(tokenB, foreign, null)).isEqualTo(100.00);
    }

    @Test
    void accountOfOtherScope_rejected_nothingSaved() throws Exception {
        long spaceId = createSpace(tokenA);
        long personal = createAccount(tokenA, "Banco", "100.00", null);
        long shared = createAccount(tokenA, "Comun", "100.00", spaceId);

        // Personal account on a space movement...
        postWithAccount(tokenA, spaceCategory(tokenA, spaceId, "EXPENSE"), TransactionType.EXPENSE, "8.26",
                personal, spaceId)
                .andExpect(status().isNotFound());
        // ...and a space account on a personal one
        postWithAccount(tokenA, expenseA.getId(), TransactionType.EXPENSE, "8.26", shared, null)
                .andExpect(status().isNotFound());

        assertThat(movementCount(tokenA, null)).isZero();
        assertThat(movementCount(tokenA, spaceId)).isZero();
        assertThat(balance(tokenA, personal, null)).isEqualTo(100.00);
        assertThat(balance(tokenA, shared, spaceId)).isEqualTo(100.00);
    }

    @Test
    void archivedAccount_conflict_nothingSaved() throws Exception {
        long account = createAccount(tokenA, "Banco", "100.00", null);
        updateAccount(account, "100.00", true);

        postWithAccount(tokenA, expenseA.getId(), TransactionType.EXPENSE, "8.26", account, null)
                .andExpect(status().isConflict());

        assertThat(movementCount(tokenA, null)).isZero();
        assertThat(balance(tokenA, account, null)).isEqualTo(100.00);
    }

    @Test
    void updateAmount_appliesDifference() throws Exception {
        long account = createAccount(tokenA, "Banco", "308.26", null);
        long movement = expenseWithAccount(account, "8.26");

        putMovement(movement, expenseA.getId(), TransactionType.EXPENSE, "10.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountBalance").value(298.26));

        assertThat(balance(tokenA, account, null)).isEqualTo(298.26);
    }

    @Test
    void updateType_reversesAndApplies() throws Exception {
        long account = createAccount(tokenA, "Banco", "100.00", null);
        long movement = expenseWithAccount(account, "10.00");
        assertThat(balance(tokenA, account, null)).isEqualTo(90.00);

        putMovement(movement, incomeA.getId(), TransactionType.INCOME, "10.00")
                .andExpect(status().isOk());

        assertThat(balance(tokenA, account, null)).isEqualTo(110.00);
    }

    @Test
    void updateCannotMoveTheMovementToAnotherAccount() throws Exception {
        long account = createAccount(tokenA, "Banco", "100.00", null);
        long other = createAccount(tokenA, "Otra", "100.00", null);
        long movement = expenseWithAccount(account, "10.00");

        mockMvc.perform(put("/api/transactions/" + movement)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "EXPENSE", "amount": 10.00, "date": "2026-06-10",
                                 "accountId": %d}
                                """.formatted(expenseA.getId(), other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account));

        assertThat(balance(tokenA, other, null)).isEqualTo(100.00);
        assertThat(balance(tokenA, account, null)).isEqualTo(90.00);
    }

    @Test
    void delete_restoresBalance() throws Exception {
        long account = createAccount(tokenA, "Banco", "100.00", null);
        long movement = expenseWithAccount(account, "10.00");

        mockMvc.perform(delete("/api/transactions/" + movement).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        assertThat(balance(tokenA, account, null)).isEqualTo(100.00);
    }

    @Test
    void deleteWhenAccountArchived_stillRestores() throws Exception {
        long account = createAccount(tokenA, "Banco", "100.00", null);
        long movement = expenseWithAccount(account, "10.00");
        updateAccount(account, "90.00", true);

        mockMvc.perform(delete("/api/transactions/" + movement).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        assertThat(balance(tokenA, account, null)).isEqualTo(100.00);
    }

    @Test
    void deleteWhenAccountGone_stillDeletes() throws Exception {
        long account = createAccount(tokenA, "Banco", "100.00", null);
        long movement = expenseWithAccount(account, "10.00");
        mockMvc.perform(delete("/api/accounts/" + account).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/transactions/" + movement).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        assertThat(movementCount(tokenA, null)).isZero();
    }

    @Test
    void oneExpenseWithAccount_keepsDiscrepancyAtZero() throws Exception {
        long account = createAccount(tokenA, "Banco", "308.26", null);
        baseline(tokenA);
        expenseWithAccount(account, "8.26");
        assertThat(discrepancy(tokenA, null)).isZero();

        long spaceId = createSpace(tokenA);
        long shared = createAccount(tokenA, "Comun", "100.00", spaceId);
        // A contribution without an account: the shared balance above already holds it
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "INCOME", "amount": 100.00, "date": "2026-06-10", "spaceId": %d}
                                """.formatted(spaceCategory(tokenA, spaceId, "INCOME"), spaceId)))
                .andExpect(status().isCreated());
        assertThat(discrepancy(tokenA, spaceId)).isZero();

        postWithAccount(tokenA, spaceCategory(tokenA, spaceId, "EXPENSE"), TransactionType.EXPENSE, "8.26",
                shared, spaceId)
                .andExpect(status().isCreated());

        assertThat(discrepancy(tokenA, spaceId)).isZero();
    }

    @Test
    void editingAMovementOlderThanTheBaseline_keepsDiscrepancyAtZero() throws Exception {
        long account = createAccount(tokenA, "Banco", "100.00", null);
        long movement = expenseWithAccount(account, "10.00");
        baseline(tokenA);

        putMovement(movement, expenseA.getId(), TransactionType.EXPENSE, "15.00").andExpect(status().isOk());

        assertThat(balance(tokenA, account, null)).isEqualTo(85.00);
        assertThat(discrepancy(tokenA, null)).isZero();
    }

    @Test
    void deletingAMovementOlderThanTheBaseline_keepsDiscrepancyAtZero() throws Exception {
        long account = createAccount(tokenA, "Banco", "100.00", null);
        long movement = expenseWithAccount(account, "10.00");
        baseline(tokenA);

        mockMvc.perform(delete("/api/transactions/" + movement).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        assertThat(balance(tokenA, account, null)).isEqualTo(100.00);
        assertThat(discrepancy(tokenA, null)).isZero();
    }
}
