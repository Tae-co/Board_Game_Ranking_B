package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.SeasonRankSnapshot;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Repository.SeasonRankSnapshotRepository;
import com.board_game_back.Utils.RatingConstants;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
    private static final YearMonth SEASON = YearMonth.of(2026, 9);
    private static final String SEASON_KEY = "2026-09";

    @Mock private CommunityRepository communityRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private PlayerGameRatingRepository ratingRepository;
    @Mock private SeasonRankSnapshotRepository snapshotRepository;
    // 롤오버가 SEASON_ROLLED_OVER를 남긴다 (§10). 이 테스트가 보는 건 스냅샷·리셋이다.
    @Mock private UserEventService userEventService;

    @InjectMocks private SeasonRolloverService rolloverService;

    @Captor private ArgumentCaptor<List<SeasonRankSnapshot>> snapshotCaptor;

    private Room room;
    private BoardGame 카탄;
    private PlayerGameRating 태윤;  // 1위
    private PlayerGameRating 지민;  // 2위
    private PlayerGameRating 현우;  // 3위

    @BeforeEach
    void setUp() {
        room = new Room("금요모임", "ABC123", 10L);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        room.assignCommunity(COMMUNITY_ID);

        카탄 = boardGame(10L, "카탄");
        태윤 = rating(1L, "태윤", 카탄, 30.0, 5.0);   // (30 - 15)×50 + 500 = 1250
        지민 = rating(2L, "지민", 카탄, 27.0, 6.0);   // (27 - 18)×50 + 500 =  950
        현우 = rating(3L, "현우", 카탄, 22.0, 7.0);   // (22 - 21)×50 + 500 =  550
    }

    @Test
    void rollover_스냅샷_순위가_리셋_직전_순위와_일치한다() {
        givenRolloverableRoom();
        when(ratingRepository.findByRoomIdWithMemberAndBoardGame(ROOM_ID))
            .thenReturn(List.of(지민, 현우, 태윤)); // 일부러 순서를 섞어 넣는다

        rolloverService.rollover(COMMUNITY_ID, SEASON);

        verify(snapshotRepository).saveAll(snapshotCaptor.capture());
        List<SeasonRankSnapshot> snapshots = snapshotCaptor.getValue();
        assertThat(snapshots).extracting(SeasonRankSnapshot::getRank).containsExactly(1, 2, 3);
        assertThat(snapshots).extracting(SeasonRankSnapshot::getMemberId).containsExactly(1L, 2L, 3L);
        assertThat(snapshots.get(0).getDisplayScore()).isCloseTo(1250.0, within(0.001));
        assertThat(snapshots.get(1).getDisplayScore()).isCloseTo(950.0, within(0.001));
        assertThat(snapshots.get(2).getDisplayScore()).isCloseTo(550.0, within(0.001));
    }

    @Test
    void rollover_이미_마감된_시즌은_두_번_박히지_않는다() {
        givenCommunityWithRoom();
        when(snapshotRepository.existsBySeasonKeyAndRoomId(SEASON_KEY, ROOM_ID)).thenReturn(true);

        int rolled = rolloverService.rollover(COMMUNITY_ID, SEASON);

        assertThat(rolled).isZero();
        verify(snapshotRepository, never()).saveAll(anyList());
        verify(ratingRepository, never()).saveAll(anyList());
    }

    @Test
    void rollover_후_전원의_레이팅이_초기값으로_돌아간다() {
        givenRolloverableRoom();
        when(ratingRepository.findByRoomIdWithMemberAndBoardGame(ROOM_ID))
            .thenReturn(List.of(태윤, 지민, 현우));

        rolloverService.rollover(COMMUNITY_ID, SEASON);

        for (PlayerGameRating rating : List.of(태윤, 지민, 현우)) {
            assertThat(rating.getGameStats().getRating()).isEqualTo(RatingConstants.INITIAL_MU);
            assertThat(rating.getGameStats().getRatingDeviation()).isEqualTo(RatingConstants.INITIAL_SIGMA);
            assertThat(rating.getPlayCount()).isZero();
            assertThat(rating.getGameStats().getDisplayScore()).isCloseTo(500.0, within(0.001));
        }
        verify(ratingRepository).saveAll(anyList());
    }

    @Test
    void 시즌_경계는_커뮤니티_타임존마다_다른_UTC_시각이다() {
        LocalDateTime 서울 = SeasonBoundaryService.startOfSeasonUtc(ZoneId.of("Asia/Seoul"), YearMonth.of(2026, 10));
        LocalDateTime 뉴욕 = SeasonBoundaryService.startOfSeasonUtc(ZoneId.of("America/New_York"), YearMonth.of(2026, 10));

        // 한국의 10월 1일 00:00은 UTC로 9월 30일 15:00 — UTC 기준으로 자르면 9시간이 어긋난다
        assertThat(서울).isEqualTo(LocalDateTime.of(2026, 9, 30, 15, 0));
        assertThat(뉴욕).isEqualTo(LocalDateTime.of(2026, 10, 1, 4, 0)); // EDT(UTC-4)
        assertThat(서울).isBefore(뉴욕);
    }

    @Test
    void rollover_첫_시즌이_짧은_방도_첫_달에_마감된다() {
        // 14일 규칙을 폐기했다 — 월말에 첫 경기를 한 방도 예외 없이 1일에 마감된다 (§20).
        givenRolloverableRoom();
        when(ratingRepository.findByRoomIdWithMemberAndBoardGame(ROOM_ID)).thenReturn(List.of(태윤));

        int rolled = rolloverService.rollover(COMMUNITY_ID, SEASON);

        assertThat(rolled).isEqualTo(1);
        verify(snapshotRepository).saveAll(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue()).hasSize(1);
        assertThat(태윤.getGameStats().getRating()).isEqualTo(RatingConstants.INITIAL_MU);
    }

    @Test
    void 스냅샷의_뮤와_시그마로_리셋_이전_상태를_복원할_수_있다() {
        givenRolloverableRoom();
        when(ratingRepository.findByRoomIdWithMemberAndBoardGame(ROOM_ID)).thenReturn(List.of(태윤));
        double muBefore = 태윤.getGameStats().getRating();
        double sigmaBefore = 태윤.getGameStats().getRatingDeviation();
        double displayBefore = 태윤.getGameStats().getDisplayScore();

        rolloverService.rollover(COMMUNITY_ID, SEASON);

        verify(snapshotRepository).saveAll(snapshotCaptor.capture());
        SeasonRankSnapshot snapshot = snapshotCaptor.getValue().get(0);
        assertThat(태윤.getGameStats().getRating()).isEqualTo(RatingConstants.INITIAL_MU); // 리셋됐다

        태윤.getGameStats().update(
            snapshot.toStats().getRating(),
            snapshot.toStats().getRatingDeviation(),
            snapshot.toStats().getVolatility());

        assertThat(태윤.getGameStats().getRating()).isEqualTo(muBefore);
        assertThat(태윤.getGameStats().getRatingDeviation()).isEqualTo(sigmaBefore);
        assertThat(태윤.getGameStats().getDisplayScore()).isCloseTo(displayBefore, within(0.001));
    }

    // ── fixtures ──

    /** 커뮤니티·방만 세운다. 롤오버 진행 여부는 각 테스트가 정한다. */
    private void givenCommunityWithRoom() {
        Community community = new Community("금요보드", "South Korea", null, 1L);
        ReflectionTestUtils.setField(community, "id", COMMUNITY_ID);
        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
        when(roomRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(List.of(room));
    }

    /** 아직 이 시즌을 마감하지 않은, 정상 마감 대상 방. */
    private void givenRolloverableRoom() {
        givenCommunityWithRoom();
        when(snapshotRepository.existsBySeasonKeyAndRoomId(SEASON_KEY, ROOM_ID)).thenReturn(false);
    }

    private BoardGame boardGame(Long id, String name) {
        BoardGame game = BoardGame.builder().name(name).build();
        ReflectionTestUtils.setField(game, "id", id);
        return game;
    }

    private PlayerGameRating rating(Long memberId, String nickname, BoardGame game, double mu, double sigma) {
        Member member = Member.builder().nickname(nickname).build();
        ReflectionTestUtils.setField(member, "id", memberId);

        PlayerGameRating rating = PlayerGameRating.builder()
            .member(member).boardGame(game).room(room).build();
        rating.getGameStats().update(mu, sigma, 0.0);
        rating.addPlayCount();
        return rating;
    }
}
