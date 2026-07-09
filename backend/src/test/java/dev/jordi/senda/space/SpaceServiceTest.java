package dev.jordi.senda.space;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.category.DefaultCategories;
import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.user.User;
import dev.jordi.senda.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private SpaceRepository spaceRepository;
    @Mock private SpaceMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;

    private SpaceService service;

    @BeforeEach
    void setUp() {
        service = new SpaceService(spaceRepository, memberRepository, userRepository, categoryRepository);
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
    void createSeedsDefaultCategoriesIntoTheNewSpace() {
        when(spaceRepository.save(any(Space.class))).thenAnswer(inv -> {
            Space s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 10L);
            return s;
        });

        service.create(USER_ID, new CreateSpaceRequest("Pareja"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Category>> captor = ArgumentCaptor.forClass(List.class);
        verify(categoryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(DefaultCategories.ALL.size());
        assertThat(captor.getValue()).allMatch(c -> c.getSpaceId().equals(10L));
        assertThat(captor.getValue()).allMatch(c -> c.getUserId().equals(USER_ID));
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

    @Test
    void addMemberCreatesPendingMembershipForInvitedUser() {
        when(memberRepository.existsBySpaceIdAndUserIdAndStatus(10L, USER_ID, MemberStatus.ACTIVE))
                .thenReturn(true);
        User invited = new User("her@example.com", "hash", "Ella");
        ReflectionTestUtils.setField(invited, "id", 2L);
        when(userRepository.findByEmail("her@example.com")).thenReturn(Optional.of(invited));
        when(memberRepository.findBySpaceIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        SpaceMemberResponse response = service.addMember(USER_ID, 10L,
                new AddMemberRequest("her@example.com"));

        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.status()).isEqualTo(MemberStatus.PENDING);
        ArgumentCaptor<SpaceMember> captor = ArgumentCaptor.forClass(SpaceMember.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MemberStatus.PENDING);
    }

    @Test
    void addMemberThrows404WhenInviterNotActiveMember() {
        when(memberRepository.existsBySpaceIdAndUserIdAndStatus(10L, USER_ID, MemberStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> service.addMember(USER_ID, 10L, new AddMemberRequest("her@example.com")))
                .isInstanceOf(NotFoundException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void addMemberThrows404WhenEmailUnknown() {
        when(memberRepository.existsBySpaceIdAndUserIdAndStatus(10L, USER_ID, MemberStatus.ACTIVE))
                .thenReturn(true);
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addMember(USER_ID, 10L, new AddMemberRequest("ghost@example.com")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addMemberThrows409WhenAlreadyMember() {
        when(memberRepository.existsBySpaceIdAndUserIdAndStatus(10L, USER_ID, MemberStatus.ACTIVE))
                .thenReturn(true);
        User invited = new User("her@example.com", "hash", "Ella");
        ReflectionTestUtils.setField(invited, "id", 2L);
        when(userRepository.findByEmail("her@example.com")).thenReturn(Optional.of(invited));
        when(memberRepository.findBySpaceIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(new SpaceMember(10L, 2L, MemberStatus.PENDING)));

        assertThatThrownBy(() -> service.addMember(USER_ID, 10L, new AddMemberRequest("her@example.com")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptTurnsPendingIntoActive() {
        SpaceMember pending = new SpaceMember(10L, 2L, MemberStatus.PENDING);
        when(memberRepository.findBySpaceIdAndUserId(10L, 2L)).thenReturn(Optional.of(pending));

        service.accept(2L, 10L);

        assertThat(pending.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(pending.getJoinedAt()).isNotNull();
        verify(memberRepository).save(pending);
    }

    @Test
    void acceptThrows404WhenNoMembership() {
        when(memberRepository.findBySpaceIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(2L, 10L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void declineDeletesPendingMembership() {
        SpaceMember pending = new SpaceMember(10L, 2L, MemberStatus.PENDING);
        when(memberRepository.findBySpaceIdAndUserId(10L, 2L)).thenReturn(Optional.of(pending));

        service.decline(2L, 10L);

        verify(memberRepository).delete(pending);
    }

    @Test
    void leaveDeletesActiveMembership() {
        SpaceMember active = new SpaceMember(10L, 2L, MemberStatus.ACTIVE);
        when(memberRepository.findBySpaceIdAndUserId(10L, 2L)).thenReturn(Optional.of(active));

        service.leave(2L, 10L);

        verify(memberRepository).delete(active);
    }

    @Test
    void listMembersThrows404ForNonMember() {
        when(memberRepository.existsBySpaceIdAndUserIdAndStatus(10L, 9L, MemberStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> service.listMembers(9L, 10L)).isInstanceOf(NotFoundException.class);
    }
}
