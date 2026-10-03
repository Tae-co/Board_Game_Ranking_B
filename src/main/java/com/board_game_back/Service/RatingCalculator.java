package com.board_game_back.Service;

import com.board_game_back.Entity.GlickoStats;
import java.util.List;

public interface RatingCalculator {

    /** casual = false면 CASUAL_RULES_FROM 이전 경기용 옛 규칙 (RatingConstants.isCasualRules) */
    void calculateMultiplayerRatings(List<PlayerResult> results, boolean casual);

    class PlayerResult {

        public Long memberId;
        public int placement;
        public int playCount; // 이번 판 전까지 이 방·게임에서 한 판 수
        public GlickoStats currentStats;
        public GlickoStats newStats;

        public PlayerResult(Long memberId, int placement, int playCount, GlickoStats currentStats) {
            this.memberId = memberId;
            this.placement = placement;
            this.playCount = playCount;
            this.currentStats = currentStats;
        }
    }
}
