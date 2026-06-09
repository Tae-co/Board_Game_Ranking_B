package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatingDecaySchedulerTest {

    @Mock
    private PlayerGameRatingRepository ratingRepository;

    @InjectMocks
    private RatingDecayScheduler scheduler;

    private PlayerGameRating staleRating;

    @BeforeEach
    void setUp() {
        staleRating = PlayerGameRating.builder().build();
        staleRating.getGameStats().update(28.0, 6.0, 0.0); // displayScore = 2000
        staleRating.addPlayCount();
        staleRating.updateLastPlayedAt(LocalDateTime.now().minusDays(29));
    }

    @Test
    void applyWeeklyDecay_대상_플레이어의_displayScore가_10_감소한다() {
        double displayBefore = staleRating.getGameStats().getDisplayScore();
        when(ratingRepository.findStaleActiveRatings(any(LocalDateTime.class)))
            .thenReturn(List.of(staleRating));

        scheduler.applyWeeklyDecay();

        assertThat(displayBefore - staleRating.getGameStats().getDisplayScore())
            .isCloseTo(10.0, within(0.001));
    }

    @Test
    void applyWeeklyDecay_saveAll이_호출된다() {
        when(ratingRepository.findStaleActiveRatings(any(LocalDateTime.class)))
            .thenReturn(List.of(staleRating));

        scheduler.applyWeeklyDecay();

        verify(ratingRepository).saveAll(anyList());
    }

    @Test
    void applyWeeklyDecay_대상이_없으면_점수_변화_없음() {
        when(ratingRepository.findStaleActiveRatings(any(LocalDateTime.class)))
            .thenReturn(List.of());

        double displayBefore = staleRating.getGameStats().getDisplayScore();
        scheduler.applyWeeklyDecay();

        assertThat(staleRating.getGameStats().getDisplayScore()).isEqualTo(displayBefore);
    }

    @Test
    void applyWeeklyDecay_28일_기준으로_cutoff를_계산한다() {
        LocalDateTime before = LocalDateTime.now().minusDays(28).minusSeconds(5);
        when(ratingRepository.findStaleActiveRatings(any(LocalDateTime.class)))
            .thenReturn(List.of());

        scheduler.applyWeeklyDecay();

        // findStaleActiveRatings가 28일 전 기준 시간으로 호출됐는지 검증
        verify(ratingRepository).findStaleActiveRatings(
            argThat(cutoff -> cutoff.isAfter(before) && cutoff.isBefore(LocalDateTime.now()))
        );
    }

    private static <T> T argThat(java.util.function.Predicate<T> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
