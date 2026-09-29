package com.board_game_back.Entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.board_game_back.Utils.RatingConstants;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerGameRatingDecayTest {

    private PlayerGameRating rating;

    @BeforeEach
    void setUp() {
        rating = PlayerGameRating.builder().build();
        // 플레이 후 상태 시뮬레이션: 1200점
        rating.getGameStats().update(1200.0, 0.0, 0.0);
        rating.addPlayCount();
    }

    @Test
    void applyDecay_displayScore가_깎은_만큼_감소한다() {
        rating.applyDecay(10.0);

        assertThat(rating.getGameStats().getDisplayScore()).isCloseTo(1190.0, within(0.001));
    }

    @Test
    void applyDecay_연속_3회_적용시_displayScore가_30_감소한다() {
        rating.applyDecay(10.0);
        rating.applyDecay(10.0);
        rating.applyDecay(10.0);

        assertThat(rating.getGameStats().getDisplayScore()).isCloseTo(1170.0, within(0.001));
    }

    @Test
    void getDisplayScore_음수는_0으로_clamp된다() {
        rating.getGameStats().update(-300.0, 0.0, 0.0);

        assertThat(rating.getGameStats().getDisplayScore()).isZero();
    }

    @Test
    void applyDecay_하한_500_밑으로는_깎이지_않는다() {
        rating.getGameStats().update(520.0, 0.0, 0.0);

        rating.applyDecay(100.0);

        assertThat(rating.getGameStats().getDisplayScore()).isCloseTo(500.0, within(0.001));
    }

    @Test
    void applyDecay_이미_하한_밑이면_그대로_둔다() {
        // 방장이 초기 점수를 낮게 설정한 경우
        rating.getGameStats().update(300.0, 0.0, 0.0);

        rating.applyDecay(100.0);

        // 더 깎지도, 끌어올리지도 않는다
        assertThat(rating.getGameStats().getRating()).isEqualTo(300.0);
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
        assertThat(rating.getGameStats().getRating()).isEqualTo(RatingConstants.INITIAL_RATING);
    }
}
