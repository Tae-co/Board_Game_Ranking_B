package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.MemberRole;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomMember;
import com.board_game_back.Exception.AppException;
import com.board_game_back.Exception.ErrorCode;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessGuardTest {

    private static final Long COMMUNITY_ID = 10L;
    private static final Long ROOM_ID = 100L;
    private static final Long LEGACY_ROOM_ID = 101L;  // 커뮤니티 없는 옛 게임방
    private static final Long ROOM_MEMBER = 1L;        // 게임방 멤버 (커뮤니티 밖에서 들어온 사람 포함)
    private static final Long COMMUNITY_ONLY = 2L;     // 커뮤니티 멤버, 게임방 미참가
    private static final Long OUTSIDER = 3L;

    @Mock private RoomRepository roomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;

    @InjectMocks private AccessGuard accessGuard;

    @BeforeEach
    void setUp() {
        Room room = new Room("카탄방", "CATAN1", 1L);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        room.assignCommunity(COMMUNITY_ID);
        Room legacy = new Room("옛방", "OLD001", 1L);
        ReflectionTestUtils.setField(legacy, "id", LEGACY_ROOM_ID);

        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(roomRepository.findById(LEGACY_ROOM_ID)).thenReturn(Optional.of(legacy));
        when(roomRepository.findById(999L)).thenReturn(Optional.empty());
        Member m = Member.builder().nickname("m").build();
        when(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, ROOM_MEMBER))
            .thenReturn(Optional.of(new RoomMember(room, m, MemberRole.MEMBER)));
        when(roomMemberRepository.findByRoomIdAndMemberId(LEGACY_ROOM_ID, ROOM_MEMBER))
            .thenReturn(Optional.of(new RoomMember(legacy, m, MemberRole.MEMBER)));

        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.existsByCommunityIdAndMemberId(COMMUNITY_ID, COMMUNITY_ONLY)).thenReturn(true);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            99L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private static void assertDenied(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(AppException.class).hasFieldOrPropertyWithValue("code", code);
    }

    @Test
    void 게임방은_게임방_멤버와_커뮤니티_멤버만_볼_수_있다() {
        assertThatCode(() -> accessGuard.requireRoomReader(ROOM_ID, ROOM_MEMBER)).doesNotThrowAnyException();
        assertThatCode(() -> accessGuard.requireRoomReader(ROOM_ID, COMMUNITY_ONLY)).doesNotThrowAnyException();
        assertDenied(() -> accessGuard.requireRoomReader(ROOM_ID, OUTSIDER), ErrorCode.ROOM_ACCESS_DENIED);
    }

    @Test
    void 커뮤니티_없는_옛_게임방은_게임방_멤버만_볼_수_있다() {
        assertThatCode(() -> accessGuard.requireRoomReader(LEGACY_ROOM_ID, ROOM_MEMBER)).doesNotThrowAnyException();
        assertDenied(() -> accessGuard.requireRoomReader(LEGACY_ROOM_ID, OUTSIDER), ErrorCode.ROOM_ACCESS_DENIED);
    }

    @Test
    void 없는_게임방은_권한보다_먼저_없다고_알린다() {
        assertDenied(() -> accessGuard.requireRoomReader(999L, OUTSIDER), ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    void 커뮤니티는_멤버만_볼_수_있다() {
        assertThatCode(() -> accessGuard.requireCommunityMember(COMMUNITY_ID, COMMUNITY_ONLY)).doesNotThrowAnyException();
        assertDenied(() -> accessGuard.requireCommunityMember(COMMUNITY_ID, ROOM_MEMBER), ErrorCode.COMMUNITY_ACCESS_DENIED);
        assertDenied(() -> accessGuard.requireCommunityMember(777L, COMMUNITY_ONLY), ErrorCode.COMMUNITY_NOT_FOUND);
    }

    @Test
    void 내_목록은_본인만_볼_수_있다() {
        assertThatCode(() -> accessGuard.requireSelf(OUTSIDER, OUTSIDER)).doesNotThrowAnyException();
        assertDenied(() -> accessGuard.requireSelf(ROOM_MEMBER, OUTSIDER), ErrorCode.SELF_ONLY);
    }

    @Test
    void 관리자는_모두_볼_수_있다() {
        loginAsAdmin();
        assertThatCode(() -> accessGuard.requireRoomReader(ROOM_ID, OUTSIDER)).doesNotThrowAnyException();
        assertThatCode(() -> accessGuard.requireCommunityMember(COMMUNITY_ID, OUTSIDER)).doesNotThrowAnyException();
        assertThatCode(() -> accessGuard.requireSelf(ROOM_MEMBER, OUTSIDER)).doesNotThrowAnyException();
    }
}
