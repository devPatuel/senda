package dev.jordi.senda.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Locks the row until the transaction ends. Two movements charged at once would
     * otherwise read the same balance and one of the two subtractions would be lost.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    // --- Personal (space_id IS NULL) ---
    List<Account> findByUserIdAndSpaceIdIsNull(Long userId);

    List<Account> findByUserIdAndSpaceIdIsNullAndArchivedFalse(Long userId);

    Optional<Account> findByIdAndUserIdAndSpaceIdIsNull(Long id, Long userId);

    /** Total liquid balance of the user's non-archived PERSONAL accounts. Null when none. */
    @Query("select sum(a.balance) from Account a where a.userId = :userId and a.spaceId is null and a.archived = false")
    BigDecimal sumActiveBalance(@Param("userId") Long userId);

    /**
     * Total balance of non-archived accounts across the given spaces. Returns
     * {@code null} when there are none. Callers must pass a non-empty list.
     */
    @Query("select sum(a.balance) from Account a where a.spaceId in :spaceIds and a.archived = false")
    BigDecimal sumActiveBalanceBySpaceIds(@Param("spaceIds") List<Long> spaceIds);

    // --- Space-scoped ---
    List<Account> findBySpaceId(Long spaceId);

    List<Account> findBySpaceIdAndArchivedFalse(Long spaceId);

    Optional<Account> findByIdAndSpaceId(Long id, Long spaceId);
}
