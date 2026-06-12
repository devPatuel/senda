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

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    boolean existsByCategoryId(Long categoryId);

    /**
     * Per-category totals for a user in a date range, aggregated in the
     * database (never loads transactions into memory).
     */
    @Query("""
            select new dev.jordi.senda.transaction.CategorySummary(
                c.id, c.name, c.color, c.type, sum(t.amount))
            from Transaction t
            join t.category c
            where t.userId = :userId and t.date between :from and :to
            group by c.id, c.name, c.color, c.type
            order by c.type, sum(t.amount) desc
            """)
    List<CategorySummary> summarizeByCategory(@Param("userId") Long userId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);
}
