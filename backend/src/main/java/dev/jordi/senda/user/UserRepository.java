package dev.jordi.senda.user;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    /** Includes deleted accounts on purpose: their email stays taken so they can be restored. */
    boolean existsByEmail(String email);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    List<User> findByDeletedAtIsNull(Sort sort);

    boolean existsByIdAndDeletedAtIsNull(Long id);

    /** Empty when the user does not exist (any token naming them is then invalid). */
    @Query("select u.tokenVersion from User u where u.id = :id and u.deletedAt is null")
    Optional<Integer> findTokenVersionById(@Param("id") Long id);
}
