package dev.jordi.senda.allocation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnvelopeBalanceRepository extends JpaRepository<EnvelopeBalance, Long> {

    Optional<EnvelopeBalance> findByEnvelopeId(Long envelopeId);

    List<EnvelopeBalance> findByUserId(Long userId);
}
