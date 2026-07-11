package dev.jordi.senda.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PriceEntryRepository extends JpaRepository<PriceEntry, Long> {

    List<PriceEntry> findByProductIdOrderByRecordedAtDesc(Long productId);

    // For building the list view's current prices in one query (avoids N+1).
    List<PriceEntry> findByProductIdInOrderByRecordedAtDesc(Collection<Long> productIds);

    // Latest known price for a product (any supermarket). Used by the shopping list (F10).
    Optional<PriceEntry> findTopByProductIdOrderByRecordedAtDesc(Long productId);
}
