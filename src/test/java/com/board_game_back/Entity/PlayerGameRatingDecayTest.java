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
    void getDisplayScore_음수_계산결과는_0으로_clamp된다() {
        // μ를 크게 낮춰 (μ - 3σ)×50 + 500 < 0 이 되게: (-20-15)*50+500 = -1250
        rating.getGameStats().update(-20.0, 5.0, 0.0);

        assertThat(rating.getGameStats().getDisplayScore()).isZero();
    }

    @Test
    void applyDecay_displayScore가_0_밑으로는_깎이지_않는다() {
        // μ=8, σ=6 → displayScore = (8-18)*50+500 = 0 (floor)
        rating.getGameStats().update(8.0, 6.0, 0.0);
        assertThat(rating.getGameStats().getDisplayScore()).isCloseTo(0.0, within(0.001));

        rating.applyDecay(DECAY_MU);
        rating.applyDecay(DECAY_MU);

        assertThat(rating.getGameStats().getDisplayScore()).isZero();
        assertThat(rating.getGameStats().getRating()).isCloseTo(8.0, within(0.001)); // floor μ 유지
    }

    @Test
    void applyDecay_이미_0_밑이면_μ를_그대로_둔다() {
        // 연패로 이미 floor 밑 (μ=5, σ=6 → floorMu=8)
        rating.getGameStats().update(5.0, 6.0, 0.0);
        double muBefore = rating.getGameStats().getRating();

        rating.applyDecay(DECAY_MU);

        // 더 깎지도, 끌어올리지도 않는다
        assertThat(rating.getGameStats().getRating()).isEqualTo(muBefore);
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
