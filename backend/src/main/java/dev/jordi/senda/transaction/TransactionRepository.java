package dev.jordi.senda.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    boolean existsByCategoryId(Long categoryId);
}
