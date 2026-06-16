package dev.jordi.senda.investment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetClassRepository extends JpaRepository<AssetClass, Long> {

    List<AssetClass> findByUserId(Long userId);

    Optional<AssetClass> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);
}
