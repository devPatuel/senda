package dev.jordi.senda.category;

import dev.jordi.senda.common.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUserId(Long userId);

    List<Category> findByUserIdAndActiveTrue(Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameAndType(Long userId, String name, TransactionType type);
}
