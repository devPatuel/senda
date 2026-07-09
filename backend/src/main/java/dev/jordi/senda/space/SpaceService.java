package dev.jordi.senda.space;

import dev.jordi.senda.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
