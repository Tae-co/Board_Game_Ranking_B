package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.MemberRole;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomMember;
import com.board_game_back.Exception.AppException;
import com.board_game_back.Exception.ErrorCode;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.CommunityAdminRepository;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MatchParticipantRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Repository.SeasonRankSnapshotRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 게임방 참가: 그 게임방이 속한 커뮤니티의 멤버만. 이미 들어와 있는 사람과 커뮤니티 없는 옛 게임방은 그대로. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomServiceJoinTest {

    private static final Long COMMUNITY_ID = 10L;
    private static final Long MEMBER = 1L;      // 커뮤니티 멤버
    private static final Long OUTSIDER = 2L;    // 커뮤니티 밖
    private static final Long INSIDE_ALREADY = 3L; // 커뮤니티 밖이지만 이미 게임방에 있음

    @Mock private RoomRepository roomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private PlayerGameRatingRepository playerGameRatingRepository;
    @Mock private BoardGameRepository boardGameRepository;
    @Mock private MatchRecordRepository matchRecordRepository;
    @Mock private MatchParticipantRepository matchParticipantRepository;
    @Mock private CommunityAdminRepository communityAdminRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private CommunityRepository communityRepository;
    @Mock private SeasonRankSnapshotRepository seasonRankSnapshotRepository;

    @InjectMocks private RoomService roomService;

    private Room room;
    private Room legacy;

    @BeforeEach
    void setUp() {
        room = new Room("카탄방", "CATAN1", null);
        ReflectionTestUtils.setField(room, "id", 100L);
        room.assignCommunity(COMMUNITY_ID);
        legacy = new Room("옛방", "OLD001", null);
        ReflectionTestUtils.setField(legacy, "id", 101L);
        when(roomRepository.findByInviteCode("CATAN1")).thenReturn(Optional.of(room));
        when(roomRepository.findByInviteCode("OLD001")).thenReturn(Optional.of(legacy));

        for (long id = 1; id <= 3; id++) {
            Member m = Member.builder().nickname("m" + id).build();
            ReflectionTestUtils.setField(m, "id", id);
            when(memberRepository.findById(id)).thenReturn(Optional.of(m));
            when(roomMemberRepository.findByRoomIdAndMemberId(any(), any())).thenReturn(Optional.empty());
        }
        when(roomMemberRepository.findByRoomIdAndMemberId(100L, INSIDE_ALREADY))
            .thenReturn(Optional.of(new RoomMember(room, Member.builder().nickname("m3").build(), MemberRole.MEMBER)));
        when(communityMemberRepository.existsByCommunityIdAndMemberId(COMMUNITY_ID, MEMBER)).thenReturn(true);
    }

    @Test
    void 커뮤니티_멤버는_참가한다() {
        assertThat(roomService.joinRoom("CATAN1", MEMBER)).isSameAs(room);
        verify(roomMemberRepository).save(any(RoomMember.class));
    }

    @Test
    void 커뮤니티_밖이면_코드를_알아도_참가하지_못한다() {
        assertThatThrownBy(() -> roomService.joinRoom("CATAN1", OUTSIDER))
            .isInstanceOf(AppException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.COMMUNITY_MEMBER_ONLY_JOIN_ROOM);
        verify(roomMemberRepository, never()).save(any());
    }

    @Test
    void 이미_게임방에_있는_사람은_커뮤니티_밖이어도_그대로다() {
        assertThat(roomService.joinRoom("CATAN1", INSIDE_ALREADY)).isSameAs(room);
        verify(roomMemberRepository, never()).save(any());
    }

    @Test
    void 커뮤니티_없는_옛_게임방은_코드만_본다() {
        assertThat(roomService.joinRoom("OLD001", OUTSIDER)).isSameAs(legacy);
        verify(roomMemberRepository).save(any(RoomMember.class));
    }
}
