package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomSeason;
import com.board_game_back.Entity.SeasonRankSnapshot;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Repository.RoomSeasonRepository;
import com.board_game_back.Repository.SeasonRankSnapshotRepository;
import com.board_game_back.Utils.RatingConstants;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SeasonRolloverServiceTest {

    private static final Long COMMUNITY_ID = 1L;
    private static final Long ROOM_ID = 100L;
    private static final Long SEASON_ID = 7L;
    private static final ZoneId 서울 = ZoneId.of("Asia/Seoul");

    @Mock private RoomRepository roomRepository;
    @Mock private RoomSeasonRepository seasonRepository;
    @Mock private SeasonBoundaryService boundaryService;
    @Mock private PlayerGameRatingRepository ratingRepository;
    @Mock private SeasonRankSnapshotRepository snapshotRepository;
    // 롤오버가 SEASON_ROLLED_OVER를 남긴다 (§10). 이 테스트가 보는 건 스냅샷·리셋이다.
    @Mock private UserEventService userEventService;

    @InjectMocks private SeasonRolloverService rolloverService;

    @Captor private ArgumentCaptor<List<SeasonRankSnapshot>> snapshotCaptor;

    private Room room;
    private RoomSeason season;
    private BoardGame 카탄;
    private PlayerGameRating 태윤;  // 1위
    private PlayerGameRating 지민;  // 2위
    private PlayerGameRating 현우;  // 3위

    @BeforeEach
    void setUp() {
        room = new Room("금요모임", "ABC123", 10L);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        room.assignCommunity(COMMUNITY_ID);

        // 4주짜리 시즌이 어제 끝났다 → 마감 대상
        LocalDate 종료일 = LocalDate.now(서울).minusDays(1);
        season = RoomSeason.open(ROOM_ID, 2, "가을 시즌",
            RoomSeason.endExclusiveUtc(종료일.minusDays(28), 서울), 종료일, 서울);
        ReflectionTestUtils.setField(season, "id", SEASON_ID);

        카탄 = boardGame(10L, "카탄");
        태윤 = rating(1L, "태윤", 카탄, 1250.0);
        지민 = rating(2L, "지민", 카탄, 950.0);
        현우 = rating(3L, "현우", 카탄, 550.0);
    }

    @Test
    void rollover_스냅샷_순위가_리셋_직전_순위와_일치한다() {
        givenRolloverableRoom();
        when(ratingRepository.findByRoomIdWithMemberAndBoardGame(ROOM_ID))
            .thenReturn(List.of(지민, 현우, 태윤)); // 일부러 순서를 섞어 넣는다

        rolloverService.rollover(SEASON_ID);

        verify(snapshotRepository).saveAll(snapshotCaptor.capture());
        List<SeasonRankSnapshot> snapshots = snapshotCaptor.getValue();
        assertThat(snapshots).extracting(SeasonRankSnapshot::getRank).containsExactly(1, 2, 3);
        assertThat(snapshots).extracting(SeasonRankSnapshot::getMemberId).containsExactly(1L, 2L, 3L);
        assertThat(snapshots.get(0).getDisplayScore()).isCloseTo(1250.0, within(0.001));
        assertThat(snapshots.get(1).getDisplayScore()).isCloseTo(950.0, within(0.001));
        assertThat(snapshots.get(2).getDisplayScore()).isCloseTo(550.0, within(0.001));
    }

    @Test
    void rollover_스냅샷이_이미_있으면_두_번_박히지_않는다() {
        givenRolloverableRoom();
        when(snapshotRepository.existsByRoomSeasonId(SEASON_ID)).thenReturn(true);

        rolloverService.rollover(SEASON_ID);

        verify(snapshotRepository, never()).saveAll(anyList());
        verify(ratingRepository, never()).saveAll(anyList());
    }

    @Test
    void rollover_후_전원의_레이팅이_초기값으로_돌아간다() {
        givenRolloverableRoom();
        when(ratingRepository.findByRoomIdWithMemberAndBoardGame(ROOM_ID))
            .thenReturn(List.of(태윤, 지민, 현우));

        rolloverService.rollover(SEASON_ID);

        for (PlayerGameRating rating : List.of(태윤, 지민, 현우)) {
            assertThat(rating.getGameStats().getRating()).isEqualTo(RatingConstants.INITIAL_RATING);
            assertThat(rating.getGameStats().getRatingDeviation()).isEqualTo(RatingConstants.INITIAL_DEVIATION);
            assertThat(rating.getPlayCount()).isZero();
            assertThat(rating.getGameStats().getDisplayScore()).isCloseTo(500.0, within(0.001));
        }
        verify(ratingRepository).saveAll(anyList());
    }

    @Test
    void rollover_시즌을_닫고_같은_길이의_다음_시즌을_연다() {
        givenRolloverableRoom();
        when(ratingRepository.findByRoomIdWithMemberAndBoardGame(ROOM_ID)).thenReturn(List.of(태윤));

        RoomSeason next = rolloverService.rollover(SEASON_ID).orElseThrow();

        assertThat(season.isClosed()).isTrue();
        assertThat(next.getSeasonNumber()).isEqualTo(3);
        assertThat(next.getName()).isNull(); // 화면이 "시즌 3"으로 표시한다
        assertThat(next.getStartAt()).isEqualTo(season.getEndAt()); // 빈틈 없이 이어진다
        assertThat(next.endDate(서울)).isEqualTo(season.endDate(서울).plusDays(28)); // 28일짜리 시즌
    }

    @Test
    void rollover_경기가_없던_방도_시즌은_넘어간다() {
        givenRolloverableRoom();
        when(ratingRepository.findByRoomIdWithMemberAndBoardGame(ROOM_ID)).thenReturn(List.of());

        RoomSeason next = rolloverService.rollover(SEASON_ID).orElseThrow();

        assertThat(season.isClosed()).isTrue();
        assertThat(next.getSeasonNumber()).isEqualTo(3);
        verify(snapshotRepository, never()).saveAll(anyList());
    }

    @Test
    void rollover_종료_전_시즌은_건드리지_않는다() {
        RoomSeason 진행중 = RoomSeason.open(ROOM_ID, 1, "시즌", LocalDateTime.now(), LocalDate.now(서울).plusDays(3), 서울);
        when(seasonRepository.findById(SEASON_ID)).thenReturn(Optional.of(진행중));

        assertThat(rolloverService.rollover(SEASON_ID)).isEmpty();
        assertThat(진행중.isClosed()).isFalse();
        verify(ratingRepository, never()).saveAll(anyList());
    }

    @Test
    void rollover_방이_삭제됐으면_시즌만_닫고_다음_시즌은_없다() {
        when(seasonRepository.findById(SEASON_ID)).thenReturn(Optional.of(season));
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

        assertThat(rolloverService.rollover(SEASON_ID)).isEmpty();
        assertThat(season.isClosed()).isTrue();
        verify(seasonRepository, never()).save(any());
    }

    @Test
    void 스냅샷의_뮤와_시그마로_리셋_이전_상태를_복원할_수_있다() {
        givenRolloverableRoom();
        when(ratingRepository.findByRoomIdWithMemberAndBoardGame(ROOM_ID)).thenReturn(List.of(태윤));
        double muBefore = 태윤.getGameStats().getRating();
        double sigmaBefore = 태윤.getGameStats().getRatingDeviation();
        double displayBefore = 태윤.getGameStats().getDisplayScore();

        rolloverService.rollover(SEASON_ID);

        verify(snapshotRepository).saveAll(snapshotCaptor.capture());
        SeasonRankSnapshot snapshot = snapshotCaptor.getValue().get(0);
        assertThat(태윤.getGameStats().getRating()).isEqualTo(RatingConstants.INITIAL_RATING); // 리셋됐다

        태윤.getGameStats().update(
            snapshot.toStats().getRating(),
            snapshot.toStats().getRatingDeviation(),
            snapshot.toStats().getVolatility());

        assertThat(태윤.getGameStats().getRating()).isEqualTo(muBefore);
        assertThat(태윤.getGameStats().getRatingDeviation()).isEqualTo(sigmaBefore);
        assertThat(태윤.getGameStats().getDisplayScore()).isCloseTo(displayBefore, within(0.001));
    }

    // ── fixtures ──

    /** 종료 시각이 지난 시즌과 그 방. */
    private void givenRolloverableRoom() {
        when(seasonRepository.findById(SEASON_ID)).thenReturn(Optional.of(season));
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(boundaryService.zoneOfRoom(ROOM_ID)).thenReturn(서울);
        when(seasonRepository.save(any(RoomSeason.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private BoardGame boardGame(Long id, String name) {
        BoardGame game = BoardGame.builder().name(name).build();
        ReflectionTestUtils.setField(game, "id", id);
        return game;
    }

    private PlayerGameRating rating(Long memberId, String nickname, BoardGame game, double score) {
        Member member = Member.builder().nickname(nickname).build();
        ReflectionTestUtils.setField(member, "id", memberId);

        PlayerGameRating rating = PlayerGameRating.builder()
            .member(member).boardGame(game).room(room).build();
        rating.getGameStats().update(score, 0.0, 0.0);
        rating.addPlayCount();
        return rating;
    }
}
