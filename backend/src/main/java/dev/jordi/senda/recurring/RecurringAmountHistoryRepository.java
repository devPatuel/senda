package dev.jordi.senda.recurring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecurringAmountHistoryRepository extends JpaRepository<RecurringAmountHistory, Long> {

    List<RecurringAmountHistory> findByRecurringIdInOrderByChangedAtAsc(List<Long> recurringIds);

    List<RecurringAmountHistory> findByRecurringIdOrderByChangedAtAsc(Long recurringId);
}
