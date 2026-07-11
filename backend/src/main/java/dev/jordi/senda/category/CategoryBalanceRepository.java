package dev.jordi.senda.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryBalanceRepository extends JpaRepository<CategoryBalance, Long> {

    List<CategoryBalance> findByUserId(Long userId);

    List<CategoryBalance> findByCategoryIdIn(List<Long> categoryIds);

    Optional<CategoryBalance> findByCategoryId(Long categoryId);
}
