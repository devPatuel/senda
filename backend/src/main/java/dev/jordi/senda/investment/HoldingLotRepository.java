package dev.jordi.senda.investment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HoldingLotRepository extends JpaRepository<HoldingLot, Long> {

    List<HoldingLot> findByHoldingIdAndUserIdOrderByDateDescIdDesc(Long holdingId, Long userId);
}
