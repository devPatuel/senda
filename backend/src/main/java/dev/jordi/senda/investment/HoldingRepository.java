package dev.jordi.senda.investment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByUserId(Long userId);

    List<Holding> findByUserIdAndAssetClassId(Long userId, Long assetClassId);

    Optional<Holding> findByIdAndUserId(Long id, Long userId);

    boolean existsByAssetClassId(Long assetClassId);
}
