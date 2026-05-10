package com.board_game_back.Utils;

public final class RatingConstants {

    public static final double INITIAL_MU = 25.0;
    public static final double INITIAL_SIGMA = 25.0 / 3.0;
    public static final double INITIAL_VOLATILITY = 0.0;

    // TrueSkill GameInfo parameters
    public static final double BETA = 25.0 / 6.0;
    public static final double DYNAMICS = 25.0 / 300.0;
    // 0.0이면 동점 시 TrueSkill이 NaN을 반환하므로 소수값으로 설정
    public static final double DRAW_PROBABILITY = 0.1;

    // displayScore = (μ - 3σ) × 50 + 1500
    public static final double DISPLAY_SCALE = 50.0;
    public static final double DISPLAY_SIGMA_FACTOR = 3.0;
    public static final double DISPLAY_OFFSET = 1500.0;

    private RatingConstants() {}
}
