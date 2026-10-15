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

    private List<MatchDto.ResultResponse> record(int d1, int b1, int d2, int b2, int d3, int b3) {
        return matchService.recordMatchResult(new MatchDto.ResultRequest(GAME_ID, ROOM_ID, List.of(
            new MatchDto.ParticipantRequest(1L, 1, null, d1, b1),
            new MatchDto.ParticipantRequest(2L, 2, null, d2, b2),
            new MatchDto.ParticipantRequest(3L, 3, null, d3, b3))), 1L);
    }

    @Test
    void 칩_게임은_손익을_바닥_없이_누적하고_Elo와_전체_랭킹은_건드리지_않는다() {
        // 바이인 10,000: p1 우승(1회), p2 2회, p3 1회 → +30,000 / -20,000 / -10,000
        List<MatchDto.ResultResponse> result = record(30000, 1, -20000, 2, -10000, 1);

        assertThat(result).extracting(MatchDto.ResultResponse::ratingChange).containsExactly(30000.0, -20000.0, -10000.0);
        assertThat(ratings.get(1L).chipNet()).isEqualTo(30000.0);
        assertThat(ratings.get(2L).chipNet()).isEqualTo(-20000.0);
        assertThat(ratings.get(1L).getWinCount()).isEqualTo(1);
        assertThat(ratings.get(2L).getLoseCount()).isEqualTo(1);
        verify(ratingCalculator, never()).calculateMultiplayerRatings(anyList(), anyBoolean());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void 리바이는_판마다_바이인_횟수에서_1을_뺀_만큼_쌓인다() {
        record(30000, 1, -20000, 2, -10000, 1);
        record(-5000, 2, 15000, 3, -10000, 1);

        assertThat(ratings.get(1L).getRebuyCount()).isEqualTo(1);
        assertThat(ratings.get(2L).getRebuyCount()).isEqualTo(3);
        assertThat(ratings.get(3L).getRebuyCount()).isZero();
        assertThat(ratings.get(2L).chipNet()).isEqualTo(-5000.0);
    }

    @Test
    void 칩_게임에서_손익이나_바이인_횟수가_없으면_거절한다() {
        assertThatThrownBy(() -> matchService.recordMatchResult(new MatchDto.ResultRequest(GAME_ID, ROOM_ID, List.of(
            new MatchDto.ParticipantRequest(1L, 1, null, null, 1),
            new MatchDto.ParticipantRequest(2L, 2, null, 0, 1))), 1L))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> matchService.recordMatchResult(new MatchDto.ResultRequest(GAME_ID, ROOM_ID, List.of(
            new MatchDto.ParticipantRequest(1L, 1, null, 100, 0),
            new MatchDto.ParticipantRequest(2L, 2, null, -100, 1))), 1L))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
