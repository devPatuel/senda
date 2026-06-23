package dev.jordi.senda.recurring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecurringPaymentRepository extends JpaRepository<RecurringPayment, Long> {

    List<RecurringPayment> findByUserId(Long userId);

    Optional<RecurringPayment> findByIdAndUserId(Long id, Long userId);
}
