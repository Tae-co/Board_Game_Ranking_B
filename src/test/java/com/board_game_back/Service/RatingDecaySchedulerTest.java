package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.MatchParticipantRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RatingDecaySchedulerTest {

    private static final Long ROOM_ID = 100L;
    private static final Long GAME_ID = 10L;
    private static final Long MEMBER_ID = 1L;

    @Mock private PlayerGameRatingRepository ratingRepository;
    @Mock private MatchParticipantRepository participantRepository;

    @InjectMocks private RatingDecayScheduler scheduler;

    private Room room;
    private BoardGame game;

    @BeforeEach
    void setUp() {
        room = new Room("금요모임", "ABC123", GAME_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        game = BoardGame.builder().name("카탄").build();
        ReflectionTestUtils.setField(game, "id", GAME_ID);
    }

    @Test
    void 승리_평균의_80퍼센트만큼_깎는다() {
        // 승리 평균 400 → 표시 점수 320 감소
        PlayerGameRating rating = staleRating(1250.0);
        givenStale(rating);
        givenPlayerWinAverage(400.0);

        scheduler.applyWeeklyDecay();

        assertThat(rating.getGameStats().getDisplayScore()).isCloseTo(930.0, within(0.001));
    }

    @Test
    void 사람마다_다른_폭으로_깎인다() {
        PlayerGameRating 신규 = staleRating(1250.0);
        PlayerGameRating 고인물 = staleRating(1250.0, 2L);     // 같은 출발점
        givenStale(신규, 고인물);
        when(participantRepository.findWinRatingChangeAvgByPlayer()).thenReturn(List.<Object[]>of(
            new Object[]{ROOM_ID, GAME_ID, MEMBER_ID, 400.0},   // 신규: 1승에 +400
            new Object[]{ROOM_ID, GAME_ID, 2L, 25.0}            // 고인물: 1승에 +25
        ));
        when(participantRepository.findWinRatingChangeAvgByRoomGame()).thenReturn(List.of());

        scheduler.applyWeeklyDecay();

        assertThat(신규.getGameStats().getDisplayScore()).isCloseTo(930.0, within(0.001));  // -320
        assertThat(고인물.getGameStats().getDisplayScore()).isCloseTo(1230.0, within(0.001)); // -20
    }

    @Test
    void 승리가_0회면_방_게임_평균을_쓴다() {
        PlayerGameRating rating = staleRating(1250.0);
        givenStale(rating);
        when(participantRepository.findWinRatingChangeAvgByPlayer()).thenReturn(List.of()); // 내 승리 없음
        when(participantRepository.findWinRatingChangeAvgByRoomGame()).thenReturn(List.<Object[]>of(
            new Object[]{ROOM_ID, GAME_ID, 200.0}
        ));

        scheduler.applyWeeklyDecay();

        assertThat(rating.getGameStats().getDisplayScore()).isCloseTo(1090.0, within(0.001)); // -160
    }

    @Test
    void 표시_점수_500_밑으로는_내려가지_않는다() {
        PlayerGameRating rating = staleRating(600.0); // 320을 깎으면 280이 될 자리
        givenStale(rating);
        givenPlayerWinAverage(400.0);

        scheduler.applyWeeklyDecay();

        assertThat(rating.getGameStats().getDisplayScore()).isCloseTo(500.0, within(0.001));
    }

    @Test
    void 갓_리셋된_레이팅은_깎이지_않는다() {
        // 리셋 직후는 시작 점수 500 = 하한에 정확히 있다
        PlayerGameRating rating = staleRating(500.0);
        givenStale(rating);
        givenPlayerWinAverage(400.0);

        scheduler.applyWeeklyDecay();

        assertThat(rating.getGameStats().getRating()).isEqualTo(500.0);
        assertThat(rating.getGameStats().getDisplayScore()).isCloseTo(500.0, within(0.001));
    }

    @Test
    void 승리_표본이_아예_없으면_깎지_않는다() {
        PlayerGameRating rating = staleRating(1250.0);
        givenStale(rating);
        when(participantRepository.findWinRatingChangeAvgByPlayer()).thenReturn(List.of());
        when(participantRepository.findWinRatingChangeAvgByRoomGame()).thenReturn(List.of());

        scheduler.applyWeeklyDecay();

        assertThat(rating.getGameStats().getDisplayScore()).isCloseTo(1250.0, within(0.001));
    }

    @Test
    void 대상이_없으면_집계도_조회하지_않는다() {
        when(ratingRepository.findStaleActiveRatings(any(LocalDateTime.class))).thenReturn(List.of());

        scheduler.applyWeeklyDecay();

        verify(participantRepository, org.mockito.Mockito.never()).findWinRatingChangeAvgByPlayer();
        verify(ratingRepository, org.mockito.Mockito.never()).saveAll(anyList());
    }

    @Test
    void cutoff를_7일_기준으로_계산한다() {
        LocalDateTime 하한 = LocalDateTime.now().minusDays(7).minusSeconds(5);
        LocalDateTime 상한 = LocalDateTime.now().minusDays(7).plusSeconds(5);
        when(ratingRepository.findStaleActiveRatings(any(LocalDateTime.class))).thenReturn(List.of());

        scheduler.applyWeeklyDecay();

        verify(ratingRepository).findStaleActiveRatings(
            org.mockito.ArgumentMatchers.argThat(
                cutoff -> cutoff.isAfter(하한) && cutoff.isBefore(상한)));
    }

    @Test
    void saveAll이_호출된다() {
        PlayerGameRating rating = staleRating(1250.0);
        givenStale(rating);
        givenPlayerWinAverage(400.0);

        scheduler.applyWeeklyDecay();

        verify(ratingRepository).saveAll(anyList());
    }

    // ── fixtures ──

    private void givenStale(PlayerGameRating... ratings) {
        when(ratingRepository.findStaleActiveRatings(any(LocalDateTime.class))).thenReturn(List.of(ratings));
    }

    private void givenPlayerWinAverage(double average) {
        when(participantRepository.findWinRatingChangeAvgByPlayer()).thenReturn(List.<Object[]>of(
            new Object[]{ROOM_ID, GAME_ID, MEMBER_ID, average}
        ));
        when(participantRepository.findWinRatingChangeAvgByRoomGame()).thenReturn(List.of());
    }

    private PlayerGameRating staleRating(double score) {
        return staleRating(score, MEMBER_ID);
    }

    private PlayerGameRating staleRating(double score, Long memberId) {
        Member member = Member.builder().nickname("멤버" + memberId).build();
        ReflectionTestUtils.setField(member, "id", memberId);

        PlayerGameRating rating = PlayerGameRating.builder()
            .member(member).boardGame(game).room(room).build();
        rating.getGameStats().update(score, 0.0, 0.0);
        rating.addPlayCount();
        rating.updateLastPlayedAt(LocalDateTime.now().minusDays(8));
        return rating;
    }
}
