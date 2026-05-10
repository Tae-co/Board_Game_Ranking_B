package com.board_game_back.Service;

import com.board_game_back.Entity.GlickoStats;
import java.util.List;

public interface RatingCalculator {

    void calculateMultiplayerRatings(List<PlayerResult> results);

    class PlayerResult {

        public Long memberId;
        public int placement;
        public GlickoStats currentStats;
        public GlickoStats newStats;

        public PlayerResult(Long memberId, int placement, GlickoStats currentStats) {
            this.memberId = memberId;
            this.placement = placement;
            this.currentStats = currentStats;
        }
    }
}
