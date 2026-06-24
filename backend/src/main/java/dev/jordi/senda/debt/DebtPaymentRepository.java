package dev.jordi.senda.debt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DebtPaymentRepository extends JpaRepository<DebtPayment, Long> {

    List<DebtPayment> findByDebtIdOrderByDateDescIdDesc(Long debtId);

    /**
     * Sum of all payment amounts for a given debt; aggregated in the DB.
     * Returns zero (via COALESCE) when there are no payments yet.
     */
    @Query("select coalesce(sum(p.amount), 0) from DebtPayment p where p.debtId = :debtId")
    BigDecimal sumByDebtId(@Param("debtId") Long debtId);

    /**
     * Paid totals for many debts in a single query, to avoid an N+1 when listing.
     * Each row is {@code [debtId, sum]}; debts with no payments are simply absent.
     *
     * <p>Scoped by {@code userId} as defense in depth: every tenant aggregation
     * must filter by user, not trust that the caller already narrowed the ids.
     */
    @Query("select p.debtId, coalesce(sum(p.amount), 0) from DebtPayment p "
            + "where p.userId = :userId and p.debtId in :debtIds group by p.debtId")
    List<Object[]> sumByDebtIds(@Param("userId") Long userId,
                                @Param("debtIds") Collection<Long> debtIds);

    Optional<DebtPayment> findByIdAndUserId(Long id, Long userId);
}
