package dev.jordi.senda.investment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByUserId(Long userId);

    List<Holding> findByUserIdAndAssetClassId(Long userId, Long assetClassId);

    Optional<Holding> findByIdAndUserId(Long id, Long userId);

    /**
     * Locks the row until the transaction ends: two buys or sells at once would
     * otherwise read the same quantity and one of the two changes would be lost
     * (or a double sell would go below zero).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Holding h where h.id = :id and h.userId = :userId")
    Optional<Holding> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByAssetClassId(Long assetClassId);
}
