package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.MemberRole;
import com.board_game_back.Entity.PlayerGameRating;
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

/** 방장의 LP(시작 점수) 수정 — 점수 줄은 (멤버, 게임, 방)마다 하나라 방의 게임으로 좁혀 찾는다 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomServiceUpdateRatingTest {

    private static final Long ROOM_ID = 100L;
    private static final Long GAME_ID = 1L;
    private static final Long HOST = 1L;
    private static final Long TARGET = 2L;

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
    private Member target;

    @BeforeEach
    void setUp() {
        room = new Room("카탄방", "CATAN1", GAME_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        Member host = Member.builder().nickname("방장").build();
        ReflectionTestUtils.setField(host, "id", HOST);
        target = Member.builder().nickname("멤버").build();
        ReflectionTestUtils.setField(target, "id", TARGET);
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, HOST))
            .thenReturn(Optional.of(new RoomMember(room, host, MemberRole.HOST)));
    }

    private PlayerGameRating rating(long gameId) {
        BoardGame game = BoardGame.builder().name("g" + gameId).build();
        ReflectionTestUtils.setField(game, "id", gameId);
        return PlayerGameRating.builder().member(target).boardGame(game).room(room).build();
    }

    @Test
    void 방의_게임_점수_줄만_바꾼다_다른_게임_줄이_남아_있어도() {
        PlayerGameRating roomGame = rating(GAME_ID);
        when(playerGameRatingRepository.findByMember_IdAndBoardGame_IdAndRoom_Id(TARGET, GAME_ID, ROOM_ID))
            .thenReturn(Optional.of(roomGame));

        roomService.updateMemberRating(ROOM_ID, TARGET, HOST, 620);

        assertThat(roomGame.getGameStats().getRating()).isEqualTo(620);
        verify(playerGameRatingRepository).save(roomGame);
        verify(playerGameRatingRepository, never()).findByMember_IdAndRoom_Id(anyLong(), anyLong());
    }

    @Test
    void 게임이_없는_옛날_방은_멤버와_방으로_찾는다() {
        ReflectionTestUtils.setField(room, "boardGameId", null);
        PlayerGameRating only = rating(GAME_ID);
        when(playerGameRatingRepository.findByMember_IdAndRoom_Id(TARGET, ROOM_ID)).thenReturn(Optional.of(only));

        roomService.updateMemberRating(ROOM_ID, TARGET, HOST, 450);

        assertThat(only.getGameStats().getRating()).isEqualTo(450);
    }

    @Test
    void 방의_게임_점수_줄이_없으면_거절한다() {
        when(playerGameRatingRepository.findByMember_IdAndBoardGame_IdAndRoom_Id(TARGET, GAME_ID, ROOM_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.updateMemberRating(ROOM_ID, TARGET, HOST, 600))
            .isInstanceOf(AppException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.RATING_NOT_FOUND);
    }
}
