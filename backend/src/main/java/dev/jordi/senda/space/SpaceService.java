package dev.jordi.senda.space;

import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.user.User;
import dev.jordi.senda.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository memberRepository;
    private final UserRepository userRepository;

    public SpaceService(SpaceRepository spaceRepository,
                        SpaceMemberRepository memberRepository,
                        UserRepository userRepository) {
        this.spaceRepository = spaceRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public SpaceResponse create(Long userId, CreateSpaceRequest request) {
        Space space = spaceRepository.save(new Space(userId, request.name()));
        memberRepository.save(new SpaceMember(space.getId(), userId, MemberStatus.ACTIVE));
        return SpaceResponse.of(space, MemberStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<SpaceResponse> listMine(Long userId) {
        List<SpaceMember> memberships = memberRepository.findByUserId(userId);
        List<Long> spaceIds = memberships.stream().map(SpaceMember::getSpaceId).toList();
        Map<Long, MemberStatus> statusBySpace = memberships.stream()
                .collect(Collectors.toMap(SpaceMember::getSpaceId, SpaceMember::getStatus));
        return spaceRepository.findAllById(spaceIds).stream()
                .map(space -> SpaceResponse.of(space, statusBySpace.get(space.getId())))
                .sorted(Comparator.comparing(SpaceResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public SpaceMemberResponse addMember(Long userId, Long spaceId, AddMemberRequest request) {
        // Only an active member may invite; a non-member must not learn the space exists
        if (!memberRepository.existsBySpaceIdAndUserIdAndStatus(spaceId, userId, MemberStatus.ACTIVE)) {
            throw new NotFoundException("Space not found");
        }
        User invited = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (memberRepository.findBySpaceIdAndUserId(spaceId, invited.getId()).isPresent()) {
            throw new ConflictException("User is already a member of this space");
        }
        memberRepository.save(new SpaceMember(spaceId, invited.getId(), MemberStatus.PENDING));
        return new SpaceMemberResponse(invited.getId(), invited.getEmail(), invited.getName(),
                MemberStatus.PENDING);
    }

    @Transactional
    public void accept(Long userId, Long spaceId) {
        SpaceMember membership = memberRepository.findBySpaceIdAndUserId(spaceId, userId)
                .orElseThrow(() -> new NotFoundException("Space not found"));
        if (membership.getStatus() == MemberStatus.ACTIVE) {
            throw new ConflictException("Membership is already active");
        }
        membership.setStatus(MemberStatus.ACTIVE);
        membership.setJoinedAt(Instant.now());
        memberRepository.save(membership);
    }

    @Transactional
    public void decline(Long userId, Long spaceId) {
        SpaceMember membership = memberRepository.findBySpaceIdAndUserId(spaceId, userId)
                .orElseThrow(() -> new NotFoundException("Space not found"));
        if (membership.getStatus() == MemberStatus.ACTIVE) {
            throw new ConflictException("Cannot decline an active membership; leave instead");
        }
        memberRepository.delete(membership);
    }

    @Transactional
    public void leave(Long userId, Long spaceId) {
        SpaceMember membership = memberRepository.findBySpaceIdAndUserId(spaceId, userId)
                .orElseThrow(() -> new NotFoundException("Space not found"));
        memberRepository.delete(membership);
    }

    @Transactional(readOnly = true)
    public List<SpaceMemberResponse> listMembers(Long userId, Long spaceId) {
        if (!memberRepository.existsBySpaceIdAndUserIdAndStatus(spaceId, userId, MemberStatus.ACTIVE)) {
            throw new NotFoundException("Space not found");
        }
        List<SpaceMember> members = memberRepository.findBySpaceId(spaceId);
        Map<Long, User> usersById = userRepository.findAllById(
                        members.stream().map(SpaceMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return members.stream()
                .map(m -> {
                    User u = usersById.get(m.getUserId());
                    return new SpaceMemberResponse(m.getUserId(),
                            u != null ? u.getEmail() : null,
                            u != null ? u.getName() : null,
                            m.getStatus());
                })
                .toList();
    }
}
