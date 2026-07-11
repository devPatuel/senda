package dev.jordi.senda.shoppinglist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, Long> {
    List<ShoppingListItem> findByUserId(Long userId);
    Optional<ShoppingListItem> findByIdAndUserId(Long id, Long userId);
    Optional<ShoppingListItem> findByUserIdAndProductId(Long userId, Long productId);
    List<ShoppingListItem> findByUserIdAndCheckedTrue(Long userId);
}
