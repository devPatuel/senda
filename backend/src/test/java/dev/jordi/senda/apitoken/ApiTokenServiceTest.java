package dev.jordi.senda.apitoken;

import dev.jordi.senda.TestcontainersConfiguration;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.user.User;
import dev.jordi.senda.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ApiTokenServiceTest {

    @Autowired
    ApiTokenService service;
    @Autowired
    ApiTokenRepository repo;
    @Autowired
    UserRepository userRepository;

    private Long newUser(String email) {
        return userRepository.save(new User(email, "x", "U")).getId();
    }

    @Test
    void generatedTokenHasPrefixAndIsStoredHashedOnly() {
        Long user = newUser("tok-svc-a@test.dev");
        var generated = service.generate(user, "iPhone");

        assertThat(generated.value()).startsWith("senda_pat_");
        // The clear value is NOT stored in the DB (only its hash)
        assertThat(repo.findAll()).noneMatch(t -> generated.value().equals(t.getTokenHash()));
        // It resolves back to the right user
        assertThat(service.resolveUserId(generated.value())).contains(user);
    }

    @Test
    void revokedTokenNoLongerResolves() {
        Long user = newUser("tok-svc-b@test.dev");
        var generated = service.generate(user, "iPhone");
        service.revoke(user, generated.id());
        assertThat(service.resolveUserId(generated.value())).isEmpty();
    }

    @Test
    void unknownOrMalformedTokenDoesNotResolve() {
        assertThat(service.resolveUserId("senda_pat_deadbeef")).isEmpty();
        assertThat(service.resolveUserId("garbage")).isEmpty();
        assertThat(service.resolveUserId(null)).isEmpty();
    }

    @Test
    void revokeIsScopedToOwner() {
        Long owner = newUser("tok-svc-owner@test.dev");
        Long other = newUser("tok-svc-other@test.dev");
        var generated = service.generate(owner, "iPhone");

        assertThatThrownBy(() -> service.revoke(other, generated.id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(service.resolveUserId(generated.value())).contains(owner);
    }
}
