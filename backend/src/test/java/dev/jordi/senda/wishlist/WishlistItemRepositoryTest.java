package dev.jordi.senda.wishlist;

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
class WishlistItemRepositoryTest {

    @Autowired private WishlistItemRepository repository;
    @Autowired private UserRepository users;

    private Long newUser(String email) {
        return users.save(new User(email, "x", "U")).getId();
    }

    @Test
    void findByIdAndUserId_scopesByOwner() {
        Long userA = newUser("wl-repo-a@test.dev");
        Long userB = newUser("wl-repo-b@test.dev");
        WishlistItem mine = repository.save(
                new WishlistItem(userA, "NAS", "https://img", "https://shop", "para backups",
                        new BigDecimal("500.00"), 1));
        repository.save(
                new WishlistItem(userB, "Ajeno", null, null, null, null, null));

        assertThat(repository.findByIdAndUserId(mine.getId(), userA)).isPresent();
        // Cross-tenant: user B cannot see user A's item
        assertThat(repository.findByIdAndUserId(mine.getId(), userB)).isEmpty();
        assertThat(repository.findByUserId(userA)).hasSize(1);
    }
}
