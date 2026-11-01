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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real parallel requests, so deliberately not {@code @Transactional}. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransactionConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    private String token;
    private long categoryId;
    private long accountId;

    private void setUpAccount() throws Exception {
        String registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s@example.com", "password": "password123", "name": "Concurrent"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = JsonPath.read(registered, "$.token");
        long userId = ((Number) JsonPath.read(registered, "$.user.id")).longValue();
        categoryId = categoryRepository.findByUserIdAndSpaceIdIsNullAndActiveTrue(userId).stream()
                .filter(c -> c.getType() == TransactionType.EXPENSE).findFirst().orElseThrow().getId();
        String account = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Banco", "type": "BANK", "balance": 100.00, "currency": "EUR"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        accountId = ((Number) JsonPath.read(account, "$.id")).longValue();
    }

    private String body(String amount) {
        return """
                {"categoryId": %d, "type": "EXPENSE", "amount": %s, "date": "2026-06-10", "accountId": %d}
                """.formatted(categoryId, amount, accountId);
    }

    private void inParallel(Supplier<MockHttpServletRequestBuilder> request, String content) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> calls = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            calls.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(request.get()
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(content))
                        .andReturn();
            }));
        }
        start.countDown();
        for (Future<?> call : calls) {
            call.get();
        }
        pool.shutdown();
    }

    @Test
    void parallelCreates_loseNoSubtraction() throws Exception {
        setUpAccount();

        inParallel(() -> post("/api/transactions"), body("1.00"));

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
                .isEqualByComparingTo("92.00");
    }

    @Test
    void parallelEditsOfOneMovement_applyTheDifferenceOnce() throws Exception {
        setUpAccount();
        String created = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("10.00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        inParallel(() -> put("/api/transactions/" + id), body("20.00"));

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
                .isEqualByComparingTo("80.00");
    }
}
