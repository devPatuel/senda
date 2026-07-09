package dev.jordi.senda.networth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface NetWorthSnapshotRepository extends JpaRepository<NetWorthSnapshot, Long> {

    List<NetWorthSnapshot> findByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
            Long userId, LocalDate from);

    /**
     * Idempotent insert of today's snapshot. {@code ON CONFLICT DO NOTHING} makes
     * it a no-op when the (user, day) row already exists, so concurrent requests
     * never raise a unique violation that would abort the surrounding read.
     */
    @Modifying
    @Query(value = """
            INSERT INTO net_worth_snapshots
                (user_id, snapshot_date, net, liquid, investments, debts_in_favor, debts_against, couple_share)
            VALUES (:userId, :date, :net, :liquid, :investments, :debtsInFavor, :debtsAgainst, :coupleShare)
            ON CONFLICT (user_id, snapshot_date) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("userId") Long userId,
                        @Param("date") LocalDate date,
                        @Param("net") BigDecimal net,
                        @Param("liquid") BigDecimal liquid,
                        @Param("investments") BigDecimal investments,
                        @Param("debtsInFavor") BigDecimal debtsInFavor,
                        @Param("debtsAgainst") BigDecimal debtsAgainst,
                        @Param("coupleShare") BigDecimal coupleShare);
}
