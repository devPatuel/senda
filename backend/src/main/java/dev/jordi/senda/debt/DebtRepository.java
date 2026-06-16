package dev.jordi.senda.debt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DebtRepository extends JpaRepository<Debt, Long> {

    List<Debt> findByUserId(Long userId);

    List<Debt> findByUserIdAndDirection(Long userId, DebtDirection direction);

    List<Debt> findByUserIdAndSettled(Long userId, boolean settled);

    List<Debt> findByUserIdAndDirectionAndSettled(Long userId, DebtDirection direction, boolean settled);

    Optional<Debt> findByIdAndUserId(Long id, Long userId);
}
