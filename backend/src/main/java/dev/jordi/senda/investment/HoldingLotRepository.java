package dev.jordi.senda.investment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface HoldingLotRepository extends JpaRepository<HoldingLot, Long> {

    List<HoldingLot> findByHoldingIdAndUserIdOrderByDateDescIdDesc(Long holdingId, Long userId);

    /**
     * Cost of the REWARD lots per holding, in one query for a whole listing (no N+1).
     * Each row is {@code [holdingId (Long), sum (BigDecimal)]}; holdings without
     * rewards are absent.
     */
    @Query("select l.holdingId, coalesce(sum(l.quantity * l.unitPrice), 0) from HoldingLot l "
            + "where l.userId = :userId and l.holdingId in :holdingIds "
            + "and l.kind = dev.jordi.senda.investment.LotKind.REWARD group by l.holdingId")
    List<Object[]> sumRewardCostByHoldingIds(@Param("userId") Long userId,
                                             @Param("holdingIds") Collection<Long> holdingIds);
}
