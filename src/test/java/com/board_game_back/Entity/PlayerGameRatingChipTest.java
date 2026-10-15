package com.board_game_back.Entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerGameRatingChipTest {

    private static final int START = 1000;

    private PlayerGameRating rating;

    @BeforeEach
    void setUp() {
        rating = PlayerGameRating.builder().build();
    }

    /** MatchService.applyChipResult처럼 판 수까지 올린다 */
    private double play(int delta) {
        double change = rating.applyChipResult(START, delta);
        rating.addPlayCount();
        return change;
    }

    @Test
    void 안_뛴_사람의_잔고는_시작_칩이다() {
        assertThat(rating.chipBalance(START)).isEqualTo(1000.0);
    }

    @Test
    void 첫_판은_시작_칩에서_증감한다() {
        assertThat(play(300)).isEqualTo(300.0);

        assertThat(rating.chipBalance(START)).isEqualTo(1300.0);
    }

    @Test
    void 잔고보다_많이_잃으면_0에서_멈추고_실제_증감만_돌려준다() {
        play(-700);

        assertThat(play(-500)).isEqualTo(-300.0);
        assertThat(rating.chipBalance(START)).isZero();
    }

    @Test
    void 잔고_0이면_다음_판에_시작_칩을_다시_받고_리바이를_센다() {
        play(-1000);

        assertThat(play(200)).isEqualTo(200.0);
        assertThat(rating.getRebuyCount()).isEqualTo(1);
        assertThat(rating.chipBalance(START)).isEqualTo(1200.0);
    }

    @Test
    void 파산하고_다음_판을_안_뛰면_리바이하지_않는다() {
        play(-1000);

        assertThat(rating.getRebuyCount()).isZero();
        assertThat(rating.chipBalance(START)).isZero();
    }

    @Test
    void reset하면_리바이_횟수도_초기화된다() {
        play(-1000);
        play(0);

        rating.reset();

        assertThat(rating.getRebuyCount()).isZero();
        assertThat(rating.chipBalance(START)).isEqualTo(1000.0);
    }
}
