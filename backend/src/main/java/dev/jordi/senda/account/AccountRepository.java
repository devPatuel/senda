package dev.jordi.senda.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUserId(Long userId);

    List<Account> findByUserIdAndArchivedFalse(Long userId);

    Optional<Account> findByIdAndUserId(Long id, Long userId);

    /**
     * Total liquid balance of the user's non-archived accounts, aggregated in
     * the database. Returns {@code null} when the user has no such accounts.
     */
    @Query("select sum(a.balance) from Account a where a.userId = :userId and a.archived = false")
    BigDecimal sumActiveBalance(@Param("userId") Long userId);
}
