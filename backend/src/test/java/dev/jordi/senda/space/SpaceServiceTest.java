package dev.jordi.senda.space;

import dev.jordi.senda.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private SpaceRepository spaceRepository;
    @Mock private SpaceMemberRepository memberRepository;
    @Mock private UserRepository userRepository;

    private SpaceService service;

    @BeforeEach
    void setUp() {
        service = new SpaceService(spaceRepository, memberRepository, userRepository);
    }

    @Test
    void createPersistsSpaceAndActiveMembershipForCreator() {
        when(spaceRepository.save(any(Space.class))).thenAnswer(inv -> {
            Space s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 10L);
            return s;
        });

        SpaceResponse response = service.create(USER_ID, new CreateSpaceRequest("Pareja"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Pareja");
        assertThat(response.myStatus()).isEqualTo(MemberStatus.ACTIVE);

        ArgumentCaptor<SpaceMember> captor = ArgumentCaptor.forClass(SpaceMember.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getSpaceId()).isEqualTo(10L);
        assertThat(captor.getValue().getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void listMineReturnsSpacesWithMyStatus() {
        Space space = new Space(USER_ID, "Pareja");
        ReflectionTestUtils.setField(space, "id", 10L);
        SpaceMember membership = new SpaceMember(10L, USER_ID, MemberStatus.PENDING);
        when(memberRepository.findByUserId(USER_ID)).thenReturn(List.of(membership));
        when(spaceRepository.findAllById(List.of(10L))).thenReturn(List.of(space));

        List<SpaceResponse> result = service.listMine(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).myStatus()).isEqualTo(MemberStatus.PENDING);
    }
}
