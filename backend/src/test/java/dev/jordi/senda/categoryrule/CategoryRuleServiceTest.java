package dev.jordi.senda.categoryrule;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryRuleServiceTest {

    private static CategoryRule rule(long id, String matchText) {
        CategoryRule r = new CategoryRule(1L, matchText, 99L);
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    @Test
    void firstMatchIsCaseInsensitiveContains() {
        List<CategoryRule> rules = List.of(rule(1, "MERCADONA"));
        assertThat(CategoryRuleService.firstMatch(rules, "Compra mercadona 23 valencia"))
                .map(CategoryRule::getId)
                .contains(1L);
    }

    @Test
    void firstMatchPrefersTheMostSpecificRuleRegardlessOfOrder() {
        // Both match; the longer "AMAZON PRIME" should win over "AMAZON".
        List<CategoryRule> rules = List.of(rule(1, "AMAZON"), rule(2, "AMAZON PRIME"));
        assertThat(CategoryRuleService.firstMatch(rules, "Pago AMAZON PRIME mayo"))
                .map(CategoryRule::getId)
                .contains(2L);
        // Reversed input order yields the same deterministic result
        assertThat(CategoryRuleService.firstMatch(List.of(rule(2, "AMAZON PRIME"), rule(1, "AMAZON")),
                "Pago AMAZON PRIME mayo"))
                .map(CategoryRule::getId)
                .contains(2L);
    }

    @Test
    void firstMatchReturnsEmptyWhenNothingMatchesOrDescriptionBlank() {
        List<CategoryRule> rules = List.of(rule(1, "NETFLIX"));
        assertThat(CategoryRuleService.firstMatch(rules, "Compra mercadona")).isEmpty();
        assertThat(CategoryRuleService.firstMatch(rules, "  ")).isEmpty();
        assertThat(CategoryRuleService.firstMatch(rules, null)).isEqualTo(Optional.empty());
    }
}
