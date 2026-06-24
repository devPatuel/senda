package dev.jordi.senda.categoryrule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

    List<CategoryRule> findByUserId(Long userId);

    Optional<CategoryRule> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndMatchText(Long userId, String matchText);
}
