package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.MatchDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.MemberRole;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.RatingType;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomMember;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceChipTest {

    private static final Long ROOM_ID = 100L;
    private static final Long GAME_ID = 30L;

    @Mock private MatchRecordRepository matchRecordRepository;
    @Mock private PlayerGameRatingRepository ratingRepository;
    @Mock private BoardGameRepository boardGameRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private RatingCalculator ratingCalculator;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private RoomSeasonService roomSeasonService;
    @Mock private SeasonRolloverService seasonRolloverService;

    @InjectMocks private MatchService matchService;

    private Room room;
    private BoardGame 포커;
    private final Map<Long, PlayerGameRating> ratings = new HashMap<>();

    @BeforeEach
    void setUp() {
        room = new Room("포커방", "POKER1", GAME_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        room.changeStartingChips(1000);
        포커 = BoardGame.builder().name("포커").ratingType(RatingType.CHIP).build();
        ReflectionTestUtils.setField(포커, "id", GAME_ID);

        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(boardGameRepository.findById(GAME_ID)).thenReturn(Optional.of(포커));
        for (long id = 1; id <= 3; id++) {
            Member member = Member.builder().nickname("p" + id).build();
            ReflectionTestUtils.setField(member, "id", id);
            PlayerGameRating rating = PlayerGameRating.builder().member(member).boardGame(포커).room(room).build();
            ratings.put(id, rating);
            when(memberRepository.findById(id)).thenReturn(Optional.of(member));
            when(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, id))
                .thenReturn(Optional.of(new RoomMember(room, member, MemberRole.MEMBER)));
            when(ratingRepository.findByMemberAndBoardGameAndRoom(member, 포커, room)).thenReturn(Optional.of(rating));
        }
    }

    private List<MatchDto.ResultResponse> record(int d1, int d2, int d3) {
        return matchService.recordMatchResult(new MatchDto.ResultRequest(GAME_ID, ROOM_ID, List.of(
            new MatchDto.ParticipantRequest(1L, 1, null, d1),
            new MatchDto.ParticipantRequest(2L, 2, null, d2),
            new MatchDto.ParticipantRequest(3L, 3, null, d3))), 1L);
    }

    @Test
    void 칩_게임은_입력한_칩만큼_잔고가_움직이고_Elo와_전체_랭킹은_건드리지_않는다() {
        List<MatchDto.ResultResponse> result = record(500, -200, -300);

        assertThat(result).extracting(MatchDto.ResultResponse::ratingChange).containsExactly(500.0, -200.0, -300.0);
        assertThat(ratings.get(1L).chipBalance(1000)).isEqualTo(1500.0);
        assertThat(ratings.get(3L).chipBalance(1000)).isEqualTo(700.0);
        assertThat(ratings.get(1L).getWinCount()).isEqualTo(1);
        verify(ratingCalculator, never()).calculateMultiplayerRatings(anyList(), anyBoolean());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void 파산한_사람은_다음_판에_리바이한다() {
        record(1000, 0, -1000);   // p3 파산
        record(-100, -100, 200);  // p3 리바이 후 +200

        PlayerGameRating p3 = ratings.get(3L);
        assertThat(p3.getRebuyCount()).isEqualTo(1);
        assertThat(p3.chipBalance(1000)).isEqualTo(1200.0);
    }

    @Test
    void 칩_게임에서_증감이_없으면_거절한다() {
        assertThatThrownBy(() -> matchService.recordMatchResult(new MatchDto.ResultRequest(GAME_ID, ROOM_ID, List.of(
            new MatchDto.ParticipantRequest(1L, 1, null, null),
            new MatchDto.ParticipantRequest(2L, 2, null, 0))), 1L))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
