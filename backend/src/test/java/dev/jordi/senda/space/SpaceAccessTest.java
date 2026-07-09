package dev.jordi.senda.space;

import dev.jordi.senda.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceAccessTest {

    @Mock
    private SpaceMemberRepository memberRepository;

    private SpaceAccess spaceAccess;

    @BeforeEach
    void setUp() {
        spaceAccess = new SpaceAccess(memberRepository);
    }

    @Test
    void assertActiveMemberPassesForActiveMember() {
        when(memberRepository.existsBySpaceIdAndUserIdAndStatus(5L, 1L, MemberStatus.ACTIVE))
                .thenReturn(true);

        assertThatCode(() -> spaceAccess.assertActiveMember(1L, 5L)).doesNotThrowAnyException();
    }

    @Test
    void assertActiveMemberThrows404ForNonMember() {
        when(memberRepository.existsBySpaceIdAndUserIdAndStatus(5L, 2L, MemberStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> spaceAccess.assertActiveMember(2L, 5L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void activeSpaceIdsReturnsOnlyActiveMemberships() {
        SpaceMember active = new SpaceMember(7L, 1L, MemberStatus.ACTIVE);
        when(memberRepository.findByUserIdAndStatus(1L, MemberStatus.ACTIVE))
                .thenReturn(List.of(active));

        assertThat(spaceAccess.activeSpaceIds(1L)).containsExactly(7L);
    }
}
