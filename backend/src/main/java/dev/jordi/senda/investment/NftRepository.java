package dev.jordi.senda.investment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NftRepository extends JpaRepository<Nft, Long> {

    List<Nft> findByUserId(Long userId);

    Optional<Nft> findByIdAndUserId(Long id, Long userId);
}
