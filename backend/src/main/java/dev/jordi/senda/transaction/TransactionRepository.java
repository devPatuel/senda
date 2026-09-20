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

    /** Same key for a shared statement: duplicates are per space, not per member. */
    boolean existsBySpaceIdAndDateAndAmountAndDescription(
            Long spaceId, java.time.LocalDate date, java.math.BigDecimal amount, String description);

    /**
     * Per-category totals for a user in a date range, aggregated in the
     * database (never loads transactions into memory).
     */
    @Query("""
            select new dev.jordi.senda.transaction.CategorySummary(
                c.id, c.name, c.color, c.type, sum(t.amount), c.fixed, c.transfer)
            from Transaction t
            join t.category c
            where t.userId = :userId and t.spaceId is null and t.date between :from and :to
            group by c.id, c.name, c.color, c.type, c.fixed, c.transfer
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
                c.id, c.name, c.color, c.type, sum(t.amount), c.fixed, c.transfer)
            from Transaction t
            join t.category c
            where t.spaceId = :spaceId and t.date between :from and :to
            group by c.id, c.name, c.color, c.type, c.fixed, c.transfer
            order by c.type, sum(t.amount) desc
            """)
    List<CategorySummary> summarizeByCategoryForSpace(@Param("spaceId") Long spaceId,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    /**
     * Per-category expense totals for a user in a date range, aggregated in the
     * database. Used by the budget view to show monthly spend per envelope.
     * Transfer categories are excluded: moving your own money between accounts is
     * not spending, and counting it would eat an envelope that never lost money.
     */
    @Query("""
            select new dev.jordi.senda.transaction.CategorySpent(t.category.id, sum(t.amount))
            from Transaction t
            where t.userId = :userId and t.spaceId is null
              and t.type = dev.jordi.senda.common.TransactionType.EXPENSE
              and t.category.transfer = false
              and t.date between :from and :to
            group by t.category.id
            """)
    List<CategorySpent> sumExpenseByCategory(@Param("userId") Long userId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * Per-category expense totals for a couple SPACE in a date range, aggregated
     * in the database. Authorization by membership is enforced in the service;
     * this query scopes strictly to the space (never personal rows).
     */
    @Query("""
            select new dev.jordi.senda.transaction.CategorySpent(t.category.id, sum(t.amount))
            from Transaction t
            where t.spaceId = :spaceId
              and t.type = dev.jordi.senda.common.TransactionType.EXPENSE
              and t.category.transfer = false
              and t.date between :from and :to
            group by t.category.id
            """)
    List<CategorySpent> sumExpenseByCategoryForSpace(@Param("spaceId") Long spaceId,
                                                     @Param("from") LocalDate from,
                                                     @Param("to") LocalDate to);

    /**
     * Per-category expense totals for a user with no date bound. The envelope
     * carries over month to month, so what is available in a category is what was
     * assigned to it minus everything ever spent from it.
     */
    @Query("""
            select new dev.jordi.senda.transaction.CategorySpent(t.category.id, sum(t.amount))
            from Transaction t
            where t.userId = :userId and t.spaceId is null
              and t.type = dev.jordi.senda.common.TransactionType.EXPENSE
              and t.category.transfer = false
            group by t.category.id
            """)
    List<CategorySpent> sumExpenseByCategoryAllTime(@Param("userId") Long userId);

    /** All-time counterpart of {@link #sumExpenseByCategoryForSpace} for a space. */
    @Query("""
            select new dev.jordi.senda.transaction.CategorySpent(t.category.id, sum(t.amount))
            from Transaction t
            where t.spaceId = :spaceId
              and t.type = dev.jordi.senda.common.TransactionType.EXPENSE
              and t.category.transfer = false
            group by t.category.id
            """)
    List<CategorySpent> sumExpenseByCategoryAllTimeForSpace(@Param("spaceId") Long spaceId);

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
     * Per-(year, month, type) totals for a user within a date range, transfers
     * excluded. Feeds the year view, whose months must read as real income and
     * spending.
     */
    @Query("""
            select new dev.jordi.senda.transaction.MonthlyTotal(
                year(t.date), month(t.date), t.type, sum(t.amount))
            from Transaction t
            where t.userId = :userId and t.spaceId is null
              and t.category.transfer = false
              and t.date between :from and :to
            group by year(t.date), month(t.date), t.type
            """)
    List<MonthlyTotal> monthlyTotalsBetween(@Param("userId") Long userId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /**
     * Per-month totals of money moved IN by transfers, for a user or a space.
     * Kept apart from {@link #monthlyTotalsBetween}, which excludes transfers:
     * the year view needs both figures side by side.
     */
    @Query("""
            select new dev.jordi.senda.transaction.MonthlyTotal(
                year(t.date), month(t.date), t.type, sum(t.amount))
            from Transaction t
            where (:spaceId is null and t.userId = :userId and t.spaceId is null
                   or t.spaceId = :spaceId)
              and t.category.transfer = true
              and t.type = dev.jordi.senda.common.TransactionType.INCOME
              and t.date between :from and :to
            group by year(t.date), month(t.date), t.type
            """)
    List<MonthlyTotal> monthlyTransfersIn(@Param("userId") Long userId,
                                          @Param("spaceId") Long spaceId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    /** Space counterpart of {@link #monthlyTotalsBetween}; membership is checked in the service. */
    @Query("""
            select new dev.jordi.senda.transaction.MonthlyTotal(
                year(t.date), month(t.date), t.type, sum(t.amount))
            from Transaction t
            where t.spaceId = :spaceId
              and t.category.transfer = false
              and t.date between :from and :to
            group by year(t.date), month(t.date), t.type
            """)
    List<MonthlyTotal> monthlyTotalsBetweenForSpace(@Param("spaceId") Long spaceId,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

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
