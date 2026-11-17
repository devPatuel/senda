package dev.jordi.senda.investment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HoldingLotRepository extends JpaRepository<HoldingLot, Long> {

    List<HoldingLot> findByHoldingIdAndUserIdOrderByDateDescIdDesc(Long holdingId, Long userId);

    /** Every lot of the given holdings in insertion order, in one query for a whole listing (no N+1). */
    List<HoldingLot> findByUserIdAndHoldingIdInOrderByIdAsc(Long userId, Collection<Long> holdingIds);
}
