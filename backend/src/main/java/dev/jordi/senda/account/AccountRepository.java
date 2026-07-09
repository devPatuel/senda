package dev.jordi.senda.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // --- Personal (space_id IS NULL) ---
    List<Account> findByUserIdAndSpaceIdIsNull(Long userId);

    List<Account> findByUserIdAndSpaceIdIsNullAndArchivedFalse(Long userId);

    Optional<Account> findByIdAndUserIdAndSpaceIdIsNull(Long id, Long userId);

    /** Total liquid balance of the user's non-archived PERSONAL accounts. Null when none. */
    @Query("select sum(a.balance) from Account a where a.userId = :userId and a.spaceId is null and a.archived = false")
    BigDecimal sumActiveBalance(@Param("userId") Long userId);

    // --- Space-scoped ---
    List<Account> findBySpaceId(Long spaceId);

    List<Account> findBySpaceIdAndArchivedFalse(Long spaceId);

    Optional<Account> findByIdAndSpaceId(Long id, Long spaceId);
}
