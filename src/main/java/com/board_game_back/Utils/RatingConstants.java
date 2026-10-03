package com.board_game_back.Utils;

import java.time.LocalDateTime;

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
    public static final double K_PLACEMENT = 300.0; // 처음 PLACEMENT_GAMES판 — 빨리 제자리를 찾게 (500이면 2연승에 500→1000)
    public static final double K_REGULAR = 200.0;
    public static final int PLACEMENT_GAMES = 3;
    // 3인 이상에서 꼴등이 2등에게 추가로 넘기는 몫 (K 대비). 배치고사 30, 이후 20.
    public static final double SECOND_PLACE_SHARE = 0.1;

    // 캐주얼 보정: 이 점수 밑에서는 이기면 더 받고 지면 덜 잃는다 (의도된 인플레이션).
    // 이 점수부터는 보정 없이 제로섬 경쟁.
    public static final double CASUAL_CEILING = 1300.0;
    // 이 점수 밑에서는 감점 절반, 여기서 CASUAL_CEILING까지 점점 원래대로
    public static final double LOSS_HALF_BELOW = 1000.0;

    // 이 시각(UTC, MatchRecord.playedAt — 서버가 기록 시점에 찍는다) 이후 경기만 위의 캐주얼 규칙(배치 K 300·3판,
    // 점수대 보정)을 쓴다. 이전 경기는 수정·삭제로 리플레이돼도 옛 규칙으로 계산해 기존 점수를 그대로 둔다.
    public static final LocalDateTime CASUAL_RULES_FROM = LocalDateTime.of(2026, 10, 3, 5, 0); // 10-03 14:00 KST
    public static final double LEGACY_K_PLACEMENT = 500.0;
    public static final int LEGACY_PLACEMENT_GAMES = 10;

    public static boolean isCasualRules(LocalDateTime playedAt) {
        return playedAt != null && !playedAt.isBefore(CASUAL_RULES_FROM);
    }

    private RatingConstants() {}
}
