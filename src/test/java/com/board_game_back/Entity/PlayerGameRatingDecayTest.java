package com.board_game_back.Entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.board_game_back.Utils.RatingConstants;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerGameRatingDecayTest {

    private static final double DECAY_MU = 10.0 / RatingConstants.DISPLAY_SCALE; // 0.2

    private PlayerGameRating rating;

    @BeforeEach
    void setUp() {
        rating = PlayerGameRating.builder().build();
        // 플레이 후 상태 시뮬레이션: μ=28, σ=6 → displayScore=2000
        rating.getGameStats().update(28.0, 6.0, 0.0);
        rating.addPlayCount();
    }

    @Test
    void applyDecay_displayScore가_10_감소한다() {
        double before = rating.getGameStats().getDisplayScore();

        rating.applyDecay(DECAY_MU);

        double after = rating.getGameStats().getDisplayScore();
        assertThat(before - after).isCloseTo(10.0, within(0.001));
    }

    @Test
    void applyDecay_mu가_0_2_감소한다() {
        double muBefore = rating.getGameStats().getRating();

        rating.applyDecay(DECAY_MU);

        assertThat(rating.getGameStats().getRating()).isCloseTo(muBefore - 0.2, within(0.0001));
    }

    @Test
    void applyDecay_sigma는_변하지_않는다() {
        double sigmaBefore = rating.getGameStats().getRatingDeviation();

        rating.applyDecay(DECAY_MU);

        assertThat(rating.getGameStats().getRatingDeviation()).isEqualTo(sigmaBefore);
    }

    @Test
    void applyDecay_연속_3회_적용시_displayScore가_30_감소한다() {
        double before = rating.getGameStats().getDisplayScore();

        rating.applyDecay(DECAY_MU);
        rating.applyDecay(DECAY_MU);
        rating.applyDecay(DECAY_MU);

        assertThat(before - rating.getGameStats().getDisplayScore()).isCloseTo(30.0, within(0.001));
    }

    @Test
    void updateLastPlayedAt_시간이_저장된다() {
        LocalDateTime now = LocalDateTime.now();
        rating.updateLastPlayedAt(now);

        assertThat(rating.getLastPlayedAt()).isEqualTo(now);
    }

    @Test
    void reset_lastPlayedAt이_null로_초기화된다() {
        rating.updateLastPlayedAt(LocalDateTime.now());

        rating.reset();

        assertThat(rating.getLastPlayedAt()).isNull();
        assertThat(rating.getPlayCount()).isZero();
        assertThat(rating.getGameStats().getRating()).isEqualTo(RatingConstants.INITIAL_MU);
    }
}
