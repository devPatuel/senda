package dev.jordi.senda.recurring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecurringAmountHistoryRepository extends JpaRepository<RecurringAmountHistory, Long> {

    // Tie-break by id so equal changed_at timestamps keep a deterministic order
    // (previousAmount depends on the second-to-last position).
    List<RecurringAmountHistory> findByRecurringIdInOrderByChangedAtAscIdAsc(List<Long> recurringIds);

    List<RecurringAmountHistory> findByRecurringIdOrderByChangedAtAscIdAsc(Long recurringId);
}
