package dev.jordi.senda.space;

import dev.jordi.senda.common.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Central authorization guard for shared spaces. A resource that carries a
 * {@code space_id} is accessible to a user only if they are an ACTIVE member of
 * that space. A non-member gets 404 (not 403): we never reveal the space exists.
 */
@Component
public class SpaceAccess {

    private final SpaceMemberRepository memberRepository;

    public SpaceAccess(SpaceMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public void assertActiveMember(Long userId, Long spaceId) {
        if (!memberRepository.existsBySpaceIdAndUserIdAndStatus(spaceId, userId, MemberStatus.ACTIVE)) {
            throw new NotFoundException("Space not found");
        }
    }

    public List<Long> activeSpaceIds(Long userId) {
        return memberRepository.findByUserIdAndStatus(userId, MemberStatus.ACTIVE).stream()
                .map(SpaceMember::getSpaceId)
                .toList();
    }
}
