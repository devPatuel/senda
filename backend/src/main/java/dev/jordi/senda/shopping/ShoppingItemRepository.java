package dev.jordi.senda.shopping;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, Long> {

    List<ShoppingItem> findByUserId(Long userId);

    List<ShoppingItem> findByUserIdAndListType(Long userId, ShoppingListType listType);

    Optional<ShoppingItem> findByIdAndUserId(Long id, Long userId);
}
