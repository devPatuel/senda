package dev.jordi.senda.category;

import dev.jordi.senda.common.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // --- Personal (space_id IS NULL) ---
    List<Category> findByUserIdAndSpaceIdIsNull(Long userId);

    List<Category> findByUserIdAndSpaceIdIsNullAndActiveTrue(Long userId);

    Optional<Category> findByIdAndUserIdAndSpaceIdIsNull(Long id, Long userId);

    boolean existsByUserIdAndNameAndTypeAndSpaceIdIsNull(Long userId, String name, TransactionType type);

    // --- Space-scoped ---
    List<Category> findBySpaceIdAndActiveTrue(Long spaceId);

    List<Category> findBySpaceId(Long spaceId);

    Optional<Category> findByIdAndSpaceId(Long id, Long spaceId);

    boolean existsBySpaceIdAndNameAndType(Long spaceId, String name, TransactionType type);
}
