package dev.jordi.senda.category;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CategoryBudgetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String token;

    @BeforeEach
    void registerUser() throws Exception {
        token = register("budget-user@example.com", "Budget User");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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

    private String budgetJson() throws Exception {
        return mockMvc.perform(get("/api/categories/budget")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
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

    private static double num(String json, String path) {
        return ((Number) JsonPath.read(json, path)).doubleValue();
    }

    private static double categoryField(String json, String name, String field) {
        List<Object> vals = JsonPath.read(json, "$.categories[?(@.name=='" + name + "')]." + field);
        return ((Number) vals.get(0)).doubleValue();
    }

    private void createAccount(String name, String balance) throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "BANK", "balance": %s, "currency": "EUR"}
                                """.formatted(name, balance)))
                .andExpect(status().isCreated());
    }

    private void assign(long categoryId, String amount) throws Exception {
        mockMvc.perform(post("/api/categories/" + categoryId + "/assign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": %s}
                                """.formatted(amount)));
    }

    private void setTarget(long categoryId, String targetAmountJson) throws Exception {
        mockMvc.perform(post("/api/categories/" + categoryId + "/target")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAmount": %s}
                                """.formatted(targetAmountJson)))
                .andExpect(status().isOk());
    }

    private static Object categoryRaw(String json, String name, String field) {
        List<Object> vals = JsonPath.read(json, "$.categories[?(@.name=='" + name + "')]." + field);
        return vals.isEmpty() ? null : vals.get(0);
    }

    private void expense(long categoryId, String amount, LocalDate date) throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "EXPENSE", "amount": %s, "date": "%s", "description": "Compra"}
                                """.formatted(categoryId, amount, date)))
                .andExpect(status().isCreated());
    }

    private long createTransferCategory(String name) throws Exception {
        String body = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "EXPENSE", "color": "#334155", "transfer": true}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void initialBudgetHasZeroAccountsAndUnassignedCategories() throws Exception {
        String body = budgetJson();

        assertThat(num(body, "$.totalAccounts")).isZero();
        assertThat(num(body, "$.totalAssigned")).isZero();
        assertThat(num(body, "$.toAssign")).isZero();
        // 7 seeded expense categories, all with balance 0
        assertThat((List<?>) JsonPath.read(body, "$.categories")).hasSize(7);
        assertThat(categoryField(body, "Comida", "balance")).isZero();
    }

    @Test
    void accountBalanceFlowsIntoToAssign() throws Exception {
        createAccount("Banco", "1000.00");

        String body = budgetJson();
        assertThat(num(body, "$.totalAccounts")).isEqualTo(1000.0);
        assertThat(num(body, "$.totalAssigned")).isZero();
        assertThat(num(body, "$.toAssign")).isEqualTo(1000.0);
    }

    @Test
    void assignMovesMoneyFromToAssignToCategoryKeepingInvariant() throws Exception {
        createAccount("Banco", "1000.00");
        long comida = categoryId("EXPENSE", "Comida");

        assign(comida, "300.00");

        String body = budgetJson();
        assertThat(num(body, "$.totalAccounts")).isEqualTo(1000.0);
        assertThat(num(body, "$.totalAssigned")).isEqualTo(300.0);
        assertThat(num(body, "$.toAssign")).isEqualTo(700.0);     // invariant: 1000 - 300
        assertThat(categoryField(body, "Comida", "balance")).isEqualTo(300.0);

        // A negative assignment pulls money back out
        assign(comida, "-50.00");
        String body2 = budgetJson();
        assertThat(categoryField(body2, "Comida", "balance")).isEqualTo(250.0);
        assertThat(num(body2, "$.toAssign")).isEqualTo(750.0);
    }

    @Test
    void assigningToIncomeCategoryIsRejected() throws Exception {
        long nomina = categoryId("INCOME", "Nómina");

        mockMvc.perform(post("/api/categories/" + nomina + "/assign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 100.00}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void spentThisMonthReflectsExpenseTransactions() throws Exception {
        long comida = categoryId("EXPENSE", "Comida");

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "EXPENSE", "amount": 40.00, "date": "%s", "description": "Súper"}
                                """.formatted(comida, LocalDate.now())))
                .andExpect(status().isCreated());

        String body = budgetJson();
        assertThat(categoryField(body, "Comida", "spentThisMonth")).isEqualTo(40.0);
    }

    @Test
    void toAssignCountsWhatIsLeftInEnvelopes_notWhatWasPutIn() throws Exception {
        createAccount("Banco", "1000.00");
        long comida = categoryId("EXPENSE", "Comida");
        assign(comida, "400.00");

        expense(comida, "120.00", LocalDate.now());

        String body = budgetJson();
        // Spent money is gone from the envelope, so it is not held there any more:
        // accounts (1000) = what is still in envelopes (280) + what is free (720).
        assertThat(num(body, "$.totalAssigned")).isEqualTo(400.0);
        assertThat(num(body, "$.totalAvailable")).isEqualTo(280.0);
        assertThat(num(body, "$.toAssign")).isEqualTo(720.0);
    }

    @Test
    void overspendingIsReportedApartFromWhatIsFreeToAssign() throws Exception {
        createAccount("Banco", "1000.00");
        long comida = categoryId("EXPENSE", "Comida");

        // Spending with nothing assigned: the category owes 120
        expense(comida, "120.00", LocalDate.now());

        String body = budgetJson();
        assertThat(num(body, "$.overspent")).isEqualTo(120.0);
        assertThat(num(body, "$.totalAvailable")).isEqualTo(-120.0);
        // The 120 has to be assigned to cover the red category, so it is still
        // pending assignment even though the money already left the account.
        assertThat(num(body, "$.toAssign")).isEqualTo(1120.0);
    }

    @Test
    void availableIsAssignedMinusSpending() throws Exception {
        long comida = categoryId("EXPENSE", "Comida");
        assign(comida, "400.00");

        expense(comida, "120.00", LocalDate.now());

        String body = budgetJson();
        assertThat(categoryField(body, "Comida", "balance")).isEqualTo(400.0);
        assertThat(categoryField(body, "Comida", "spent")).isEqualTo(120.0);
        assertThat(categoryField(body, "Comida", "available")).isEqualTo(280.0);
    }

    @Test
    void availableGoesNegativeWhenSpendingExceedsAssigned() throws Exception {
        long comida = categoryId("EXPENSE", "Comida");
        assign(comida, "10.00");

        expense(comida, "120.00", LocalDate.now());

        assertThat(categoryField(budgetJson(), "Comida", "available")).isEqualTo(-110.0);
    }

    @Test
    void availableCountsSpendingFromEarlierMonths() throws Exception {
        long comida = categoryId("EXPENSE", "Comida");
        assign(comida, "400.00");

        expense(comida, "50.00", LocalDate.now().minusMonths(3));

        String body = budgetJson();
        // The envelope carries over: older spending still reduces what is available,
        // even though it falls outside this month's "spentThisMonth".
        assertThat(categoryField(body, "Comida", "spentThisMonth")).isZero();
        assertThat(categoryField(body, "Comida", "spent")).isEqualTo(50.0);
        assertThat(categoryField(body, "Comida", "available")).isEqualTo(350.0);
    }

    @Test
    void transfersDoNotReduceAvailable() throws Exception {
        long traspaso = createTransferCategory("Traspaso a conjunta");
        assign(traspaso, "400.00");

        expense(traspaso, "120.00", LocalDate.now());

        String body = budgetJson();
        // Moving your own money between accounts is not spending: it must not eat
        // the envelope, or every transfer would look like a purchase.
        assertThat(categoryField(body, "Traspaso a conjunta", "spent")).isZero();
        assertThat(categoryField(body, "Traspaso a conjunta", "available")).isEqualTo(400.0);
    }

    @Test
    void categoriesHaveNoTargetByDefault() throws Exception {
        String body = budgetJson();
        assertThat(categoryRaw(body, "Comida", "targetAmount")).isNull();
    }

    @Test
    void setTargetShowsInBudgetAndCanBeCleared() throws Exception {
        long comida = categoryId("EXPENSE", "Comida");

        setTarget(comida, "200.00");
        assertThat(categoryField(budgetJson(), "Comida", "targetAmount")).isEqualTo(200.0);

        // A null amount clears the target
        setTarget(comida, "null");
        assertThat(categoryRaw(budgetJson(), "Comida", "targetAmount")).isNull();
    }

    @Test
    void setTargetOnIncomeCategoryIsRejected() throws Exception {
        long nomina = categoryId("INCOME", "Nómina");

        mockMvc.perform(post("/api/categories/" + nomina + "/target")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAmount": 100.00}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void negativeTargetIsRejected() throws Exception {
        long comida = categoryId("EXPENSE", "Comida");

        mockMvc.perform(post("/api/categories/" + comida + "/target")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAmount": -5.00}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void targetOnForeignCategoryReturns404() throws Exception {
        long comida = categoryId("EXPENSE", "Comida");
        String otherToken = register("budget-other@example.com", "Other");

        mockMvc.perform(post("/api/categories/" + comida + "/target")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAmount": 50.00}
                                """))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Personal discrepancy
    // -------------------------------------------------------------------------

    private long createAccountReturningId(String name, String balance) throws Exception {
        String body = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "BANK", "balance": %s, "currency": "EUR"}
                                """.formatted(name, balance)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void setBalance(long accountId, String name, String balance) throws Exception {
        mockMvc.perform(put("/api/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "BANK", "balance": %s, "currency": "EUR"}
                                """.formatted(name, balance)))
                .andExpect(status().isOk());
    }

    private void baseline() throws Exception {
        mockMvc.perform(post("/api/accounts/baseline")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private void movement(long categoryId, String type, String amount) throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "type": "%s", "amount": %s, "date": "%s"}
                                """.formatted(categoryId, type, amount, LocalDate.now())))
                .andExpect(status().isCreated());
    }

    @Test
    void personalWithoutBaseline_hasNoDiscrepancy() throws Exception {
        createAccount("Banco", "308.26");

        String body = budgetJson();

        assertThat((Object) JsonPath.read(body, "$.discrepancy")).isNull();
        assertThat((Object) JsonPath.read(body, "$.expectedAccounts")).isNull();
    }

    @Test
    void personalBaseline_startsAtZero() throws Exception {
        createAccount("Banco", "308.26");
        baseline();

        String body = budgetJson();

        assertThat(num(body, "$.realAccounts")).isEqualTo(308.26);
        assertThat(num(body, "$.expectedAccounts")).isEqualTo(308.26);
        assertThat(num(body, "$.discrepancy")).isZero();
    }

    @Test
    void personalExpenseWithoutBalanceChange_showsDiscrepancy() throws Exception {
        createAccount("Banco", "308.26");
        baseline();
        expense(categoryId("EXPENSE", "Comida"), "8.26", LocalDate.now());

        String body = budgetJson();

        assertThat(num(body, "$.expectedAccounts")).isEqualTo(300.00);
        assertThat(num(body, "$.discrepancy")).isEqualTo(8.26);
        // The envelopes keep reading the real balance: "to assign" must not move
        assertThat(num(body, "$.totalAccounts")).isEqualTo(308.26);
    }

    @Test
    void personalBalanceCorrected_returnsToZero() throws Exception {
        long account = createAccountReturningId("Banco", "308.26");
        baseline();
        expense(categoryId("EXPENSE", "Comida"), "8.26", LocalDate.now());
        setBalance(account, "Banco", "300.00");

        assertThat(num(budgetJson(), "$.discrepancy")).isZero();
    }

    @Test
    void backdatedMovementCreatedAfterBaseline_counts() throws Exception {
        createAccount("Banco", "100.00");
        baseline();
        expense(categoryId("EXPENSE", "Comida"), "10.00", LocalDate.now().minusYears(1));

        assertThat(num(budgetJson(), "$.discrepancy")).isEqualTo(10.00);
    }

    @Test
    void movementCreatedBeforeBaseline_ignored() throws Exception {
        createAccount("Banco", "100.00");
        expense(categoryId("EXPENSE", "Comida"), "10.00", LocalDate.now());
        baseline();

        assertThat(num(budgetJson(), "$.discrepancy")).isZero();
    }

    @Test
    void transferBothLegs_noDiscrepancyChange() throws Exception {
        createAccount("Banco", "100.00");
        baseline();
        movement(createTransferCategory("Traspaso sale"), "EXPENSE", "25.00");
        movement(categoryId("INCOME", "Otros ingresos"), "INCOME", "25.00");

        assertThat(num(budgetJson(), "$.discrepancy")).isZero();
    }

    @Test
    void baselineAgain_resetsDiscrepancy() throws Exception {
        createAccount("Banco", "100.00");
        baseline();
        expense(categoryId("EXPENSE", "Comida"), "10.00", LocalDate.now());
        assertThat(num(budgetJson(), "$.discrepancy")).isEqualTo(10.00);

        baseline();

        assertThat(num(budgetJson(), "$.discrepancy")).isZero();
    }

    @Test
    void spaceDiscrepancy_unchanged() throws Exception {
        String space = mockMvc.perform(post("/api/spaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Pareja\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long spaceId = ((Number) JsonPath.read(space, "$.id")).longValue();
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Comun", "type": "BANK", "balance": 20.00, "currency": "EUR", "spaceId": %d}
                                """.formatted(spaceId)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/categories/budget")
                        .header("Authorization", "Bearer " + token)
                        .param("spaceId", String.valueOf(spaceId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(num(body, "$.realAccounts")).isEqualTo(20.00);
        assertThat(num(body, "$.expectedAccounts")).isZero();
        assertThat(num(body, "$.discrepancy")).isEqualTo(20.00);
    }
}
