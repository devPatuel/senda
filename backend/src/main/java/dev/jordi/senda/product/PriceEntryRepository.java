package dev.jordi.senda.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PriceEntryRepository extends JpaRepository<PriceEntry, Long> {

    List<PriceEntry> findByProductIdOrderByRecordedAtDesc(Long productId);

    // For building the list view's current prices in one query (avoids N+1).
    List<PriceEntry> findByProductIdInOrderByRecordedAtDesc(Collection<Long> productIds);
}
