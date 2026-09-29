package com.board_game_back.Service;

import com.board_game_back.Entity.GlickoStats;
import com.board_game_back.Utils.RatingConstants;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EloRatingCalculator implements RatingCalculator {

    @Override
    public void calculateMultiplayerRatings(List<RatingCalculator.PlayerResult> results) {
        int opponents = results.size() - 1;

        for (RatingCalculator.PlayerResult me : results) {
            double current = me.currentStats.getRating();
            if (opponents < 1) {
                me.newStats = new GlickoStats(current, me.currentStats.getRatingDeviation(), 0.0);
                continue;
            }

            // 모든 상대와 1:1로 붙었다고 보고 (실제 - 기대)를 더한다. 동순위는 0.5.
            double surprise = 0.0;
            for (RatingCalculator.PlayerResult other : results) {
                if (other == me) continue;
                double actual = me.placement < other.placement ? 1.0
                    : me.placement == other.placement ? 0.5 : 0.0;
                surprise += actual - expectedScore(current, other.currentStats.getRating());
            }

            double k = me.playCount < RatingConstants.PLACEMENT_GAMES
                ? RatingConstants.K_PLACEMENT : RatingConstants.K_REGULAR;
            double updated = current + k * surprise / opponents;
            // 하한 밑으로 끌어내리지 않는다. 이미 밑이면(방장이 낮게 설정) 더 내려가지만 않게 한다.
            double newRating = Math.max(updated, Math.min(current, RatingConstants.RATING_FLOOR));

            me.newStats = new GlickoStats(newRating, me.currentStats.getRatingDeviation(), 0.0);
        }
    }

    private static double expectedScore(double mine, double theirs) {
        return 1.0 / (1.0 + Math.pow(10.0, (theirs - mine) / RatingConstants.ELO_SCALE));
    }
}
