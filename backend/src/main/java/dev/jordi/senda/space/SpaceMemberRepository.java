package dev.jordi.senda.space;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpaceMemberRepository extends JpaRepository<SpaceMember, Long> {

    List<SpaceMember> findByUserId(Long userId);

    List<SpaceMember> findByUserIdAndStatus(Long userId, MemberStatus status);

    List<SpaceMember> findBySpaceId(Long spaceId);

    Optional<SpaceMember> findBySpaceIdAndUserId(Long spaceId, Long userId);

    boolean existsBySpaceIdAndUserIdAndStatus(Long spaceId, Long userId, MemberStatus status);
}
