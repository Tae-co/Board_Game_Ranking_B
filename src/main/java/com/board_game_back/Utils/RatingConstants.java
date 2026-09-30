package com.board_game_back.Utils;

public final class RatingConstants {

    // rating 컬럼에 Elo 점수를 그대로 저장한다. 표시 점수 = rating.
    public static final double INITIAL_RATING = 500.0;
    // ratingDeviation·volatility 컬럼은 TrueSkill 시절 흔적 — 미사용
    public static final double INITIAL_DEVIATION = 0.0;
    public static final double INITIAL_VOLATILITY = 0.0;

    // 이 밑으로는 지거나 decay 돼도 내려가지 않는다 (시작 점수와 같음)
    public static final double RATING_FLOOR = 500.0;

    // 다인원 Elo: 상대마다 1:1 기대 승률을 구해 (실제 - 기대)의 평균 × K 만큼 움직인다.
    // 비슷한 상대끼리 1등이면 +K/2, 이변일수록 K에 가까워진다.
    public static final double ELO_SCALE = 3200.0; // 점수 차가 이만큼이면 기대 승률 10:1
    public static final double K_PLACEMENT = 500.0; // 처음 PLACEMENT_GAMES판 — 빨리 제자리를 찾게
    public static final double K_REGULAR = 200.0;
    public static final int PLACEMENT_GAMES = 10;
    // 3인 이상에서 꼴등이 2등에게 추가로 넘기는 몫 (K 대비). 배치고사 50, 이후 20.
    public static final double SECOND_PLACE_SHARE = 0.1;

    private RatingConstants() {}
}
