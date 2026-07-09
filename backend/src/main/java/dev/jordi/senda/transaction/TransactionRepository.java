package dev.jordi.senda.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdAndUserIdAndSpaceIdIsNull(Long id, Long userId);

    Optional<Transaction> findByIdAndSpaceId(Long id, Long spaceId);

    boolean existsByCategoryId(Long categoryId);

    /** Dedupe key for CSV import: same day, amount and description for this user. */
    boolean existsByUserIdAndDateAndAmountAndDescriptionAndSpaceIdIsNull(
            Long userId, java.time.LocalDate date, java.math.BigDecimal amount, String description);

    /**
     * Per-category totals for a user in a date range, aggregated in the
     * database (never loads transactions into memory).
     */
    @Query("""
            select new dev.jordi.senda.transaction.CategorySummary(
                c.id, c.name, c.color, c.type, sum(t.amount))
            from Transaction t
            join t.category c
            where t.userId = :userId and t.spaceId is null and t.date between :from and :to
            group by c.id, c.name, c.color, c.type
            order by c.type, sum(t.amount) desc
            """)
    List<CategorySummary> summarizeByCategory(@Param("userId") Long userId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    /**
     * Per-category totals for a couple SPACE in a date range, aggregated in the
     * database. Authorization by membership is enforced in the service; this query
     * scopes strictly to the space (never personal rows).
     */
    @Query("""
            select new dev.jordi.senda.transaction.CategorySummary(
                c.id, c.name, c.color, c.type, sum(t.amount))
            from Transaction t
            join t.category c
            where t.spaceId = :spaceId and t.date between :from and :to
            group by c.id, c.name, c.color, c.type
            order by c.type, sum(t.amount) desc
            """)
    List<CategorySummary> summarizeByCategoryForSpace(@Param("spaceId") Long spaceId,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    /**
     * Per-category expense totals for a user in a date range, aggregated in the
     * database. Used by the budget view to show monthly spend per envelope.
     */
    @Query("""
            select new dev.jordi.senda.transaction.CategorySpent(t.category.id, sum(t.amount))
            from Transaction t
            where t.userId = :userId and t.spaceId is null
              and t.type = dev.jordi.senda.common.TransactionType.EXPENSE
              and t.date between :from and :to
            group by t.category.id
            """)
    List<CategorySpent> sumExpenseByCategory(@Param("userId") Long userId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * Per-(year, month, type) totals from {@code from} onward, aggregated in the
     * database. The service reshapes these into a dense per-month trend series.
     */
    @Query("""
            select new dev.jordi.senda.transaction.MonthlyTotal(
                year(t.date), month(t.date), t.type, sum(t.amount))
            from Transaction t
            where t.userId = :userId and t.spaceId is null and t.date >= :from
            group by year(t.date), month(t.date), t.type
            """)
    List<MonthlyTotal> monthlyTotals(@Param("userId") Long userId, @Param("from") LocalDate from);

    /**
     * Per-category expense count and total in a date range. The "ant expenses"
     * heuristic filters these in the service (count/avg/sum thresholds).
     */
    @Query("""
            select new dev.jordi.senda.transaction.CategoryExpenseStat(
                c.id, c.name, c.color, count(t), sum(t.amount))
            from Transaction t
            join t.category c
            where t.userId = :userId and t.spaceId is null
              and t.type = dev.jordi.senda.common.TransactionType.EXPENSE
              and t.date between :from and :to
            group by c.id, c.name, c.color
            """)
    List<CategoryExpenseStat> expenseStatsByCategory(@Param("userId") Long userId,
                                                     @Param("from") LocalDate from,
                                                     @Param("to") LocalDate to);

    /**
     * Distinct category ids the user has spent on since {@code from}. Used to find
     * recurring payments whose category has not been used recently.
     */
    @Query("""
            select distinct t.category.id
            from Transaction t
            where t.userId = :userId and t.spaceId is null
              and t.type = dev.jordi.senda.common.TransactionType.EXPENSE
              and t.date >= :from
            """)
    List<Long> categoryIdsWithExpenseSince(@Param("userId") Long userId, @Param("from") LocalDate from);
}
