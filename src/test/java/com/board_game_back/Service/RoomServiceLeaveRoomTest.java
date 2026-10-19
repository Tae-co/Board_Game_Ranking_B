package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.MemberRole;
import com.board_game_back.Entity.RoomMember;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 방장이 나갈 때 다음 방장 — 커뮤니티 소유권과 같은 기준(경기 수, 동점이면 먼저 들어온 사람) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomServiceLeaveRoomTest {

    private static final Long ROOM_ID = 100L;
    private static final Long HOST = 1L;
    private static final Long EARLY = 2L;   // 먼저 들어온 멤버
    private static final Long LATE = 3L;    // 나중에 들어온 멤버

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

    @InjectMocks private RoomService roomService;

    private final RoomMember host = roomMember(10L, HOST, MemberRole.HOST);
    private final RoomMember early = roomMember(11L, EARLY, MemberRole.MEMBER);
    private final RoomMember late = roomMember(12L, LATE, MemberRole.MEMBER);

    @Test
    void 경기를_가장_많이_한_멤버가_방장이_된다() {
        givenRoom();
        when(matchParticipantRepository.countByMemberInRoom(ROOM_ID)).thenReturn(List.of(
            new Object[]{HOST, 20L}, new Object[]{EARLY, 3L}, new Object[]{LATE, 8L}
        ));

        roomService.leaveRoom(ROOM_ID, HOST, HOST);

        assertThat(late.getRole()).isEqualTo(MemberRole.HOST);
        assertThat(early.getRole()).isEqualTo(MemberRole.MEMBER);
    }

    @Test
    void 경기_수가_같으면_먼저_들어온_멤버가_방장이_된다() {
        givenRoom();

        roomService.leaveRoom(ROOM_ID, HOST, HOST);

        assertThat(early.getRole()).isEqualTo(MemberRole.HOST);
        assertThat(late.getRole()).isEqualTo(MemberRole.MEMBER);
    }

    private void givenRoom() {
        when(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, HOST)).thenReturn(Optional.of(host));
        when(roomMemberRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(host, late, early));
    }

    private static RoomMember roomMember(Long id, Long memberId, MemberRole role) {
        Member member = Member.builder().nickname("m" + memberId).build();
        ReflectionTestUtils.setField(member, "id", memberId);
        RoomMember rm = new RoomMember(null, member, role);
        ReflectionTestUtils.setField(rm, "id", id);
        return rm;
    }
}
