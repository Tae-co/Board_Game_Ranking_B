package com.board_game_back.Entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerGameRatingChipTest {

    private PlayerGameRating rating;

    @BeforeEach
    void setUp() {
        rating = PlayerGameRating.builder()
            .boardGame(BoardGame.builder().name("포커").ratingType(RatingType.CHIP).build())
            .build();
    }

    /** MatchService.applyChipResult처럼 판 수까지 올린다 */
    private void play(int delta, int rebuys) {
        rating.applyChipResult(delta, rebuys);
        rating.addPlayCount();
    }

    @Test
    void 안_뛰었으면_손익은_0이다() {
        assertThat(rating.chipNet()).isZero();
    }

    @Test
    void 손익은_음수로도_내려간다() {
        play(-20000, 1);
        play(5000, 0);
        assertThat(rating.chipNet()).isEqualTo(-15000.0);
        assertThat(rating.rankingScore()).isEqualTo(-15000.0);
        assertThat(rating.getRebuyCount()).isEqualTo(1);
    }

    @Test
    void 리셋하면_손익과_리바이가_0으로_돌아간다() {
        play(30000, 2);
        rating.reset();
        assertThat(rating.chipNet()).isZero();
        assertThat(rating.getRebuyCount()).isZero();
    }
}
