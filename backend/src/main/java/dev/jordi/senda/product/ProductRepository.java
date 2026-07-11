package dev.jordi.senda.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByUserIdAndSpaceIdIsNullOrderByNameAsc(Long userId);

    Optional<Product> findByIdAndUserIdAndSpaceIdIsNull(Long id, Long userId);
}
