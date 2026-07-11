package dev.jordi.senda.product;

import dev.jordi.senda.TestcontainersConfiguration;
import dev.jordi.senda.user.User;
import dev.jordi.senda.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ProductRepositoryTest {

    @Autowired private ProductRepository products;
    @Autowired private PriceEntryRepository prices;
    @Autowired private UserRepository users;

    private Long newUser(String email) {
        return users.save(new User(email, "x", "U")).getId();
    }

    @Test
    void savesAndScopesByUserPersonal() {
        Long userA = newUser("prod-repo-a@test.dev");
        Long userB = newUser("prod-repo-b@test.dev");
        Product p = new Product(userA, "Leche", UnitType.WEIGHT, new BigDecimal("1.000"), "L");
        Product saved = products.save(p);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSpaceId()).isNull();

        // Owner sees it; another user does not (personal scope).
        assertThat(products.findByIdAndUserIdAndSpaceIdIsNull(saved.getId(), userA)).isPresent();
        assertThat(products.findByIdAndUserIdAndSpaceIdIsNull(saved.getId(), userB)).isEmpty();
        assertThat(products.findByUserIdAndSpaceIdIsNullOrderByNameAsc(userA)).hasSize(1);
    }

    @Test
    void deletingProductCascadesPriceHistory() {
        Long userA = newUser("prod-repo-c@test.dev");
        Product saved = products.save(
                new Product(userA, "Pan", UnitType.QUANTITY, new BigDecimal("1.000"), "ud"));
        prices.save(new PriceEntry(saved.getId(), new BigDecimal("1.20"), "Mercadona"));
        prices.save(new PriceEntry(saved.getId(), new BigDecimal("1.10"), "Lidl"));

        assertThat(prices.findByProductIdOrderByRecordedAtDesc(saved.getId())).hasSize(2);

        products.delete(saved);
        products.flush();

        assertThat(prices.findByProductIdOrderByRecordedAtDesc(saved.getId())).isEmpty();
    }
}
