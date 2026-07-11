package dev.jordi.senda.apitoken;

import dev.jordi.senda.TestcontainersConfiguration;
import dev.jordi.senda.user.User;
import dev.jordi.senda.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ApiTokenRepositoryTest {

    @Autowired
    ApiTokenRepository repo;
    @Autowired
    UserRepository userRepository;

    @Test
    void findsByHashAndScopesByUser() {
        // api_tokens has an FK to users, so persist real users first.
        Long userA = userRepository.save(new User("tokrepo-a@test.dev", "x", "A")).getId();
        Long userB = userRepository.save(new User("tokrepo-b@test.dev", "x", "B")).getId();

        repo.save(new ApiToken(userA, "hash-a", "iPhone Jordi"));
        repo.save(new ApiToken(userB, "hash-b", "iPhone pareja"));

        assertThat(repo.findByTokenHash("hash-a")).isPresent();
        assertThat(repo.findByTokenHash("nope")).isEmpty();
        assertThat(repo.findByUserIdOrderByCreatedAtDesc(userA)).hasSize(1);
    }
}
