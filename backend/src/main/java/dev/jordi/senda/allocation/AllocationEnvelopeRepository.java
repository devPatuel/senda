package dev.jordi.senda.allocation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AllocationEnvelopeRepository extends JpaRepository<AllocationEnvelope, Long> {

    List<AllocationEnvelope> findByUserIdOrderByPosition(Long userId);

    Optional<AllocationEnvelope> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
