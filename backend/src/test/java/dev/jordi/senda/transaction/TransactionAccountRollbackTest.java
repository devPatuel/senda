package dev.jordi.senda.transaction;

import com.jayway.jsonpath.JsonPath;
import dev.jordi.senda.TestcontainersConfiguration;
import dev.jordi.senda.account.AccountRepository;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.common.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deliberately not {@code @Transactional}: a surrounding test transaction would
 * hide whether the service's own transaction really rolls the balance back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransactionAccountRollbackTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @MockitoSpyBean
    private TransactionRepository transactionRepository;

    @Test
    void saveFailure_rollsBackBalance() throws Exception {
        String registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s@example.com", "password": "password123", "name": "Rollback"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(registered, "$.token");
        long userId = ((Number) JsonPath.read(registered, "$.user.id")).longValue();
        long categoryId = categoryRepository.findByUserIdAndSpaceIdIsNullAndActiveTrue(userId).stream()
                .filter(c -> c.getType() == TransactionType.EXPENSE).findFirst().orElseThrow().getId();
        String created = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Banco", "type": "BANK", "balance": 100.00, "currency": "EUR"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long accountId = ((Number) JsonPath.read(created, "$.id")).longValue();

        doThrow(new IllegalStateException("simulated failure")).when(transactionRepository).save(any(Transaction.class));
        try {
            mockMvc.perform(post("/api/transactions")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"categoryId": %d, "type": "EXPENSE", "amount": 8.26, "date": "2026-06-10", "accountId": %d}
                            """.formatted(categoryId, accountId)));
        } catch (Exception expectedWhenUnhandled) {
            // Whether it surfaces as a 500 or as an exception is not what is under test
        }

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00");
    }
}
