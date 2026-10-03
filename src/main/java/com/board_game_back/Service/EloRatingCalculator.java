package com.board_game_back.Service;

import com.board_game_back.Entity.GlickoStats;
import com.board_game_back.Utils.RatingConstants;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EloRatingCalculator implements RatingCalculator {

    @Override
    public void calculateMultiplayerRatings(List<RatingCalculator.PlayerResult> results, boolean casual) {
        int opponents = results.size() - 1;
        int lastPlacement = results.stream().mapToInt(r -> r.placement).max().orElse(0);
        long secondCount = results.stream().filter(r -> r.placement == 2).count();
        long lastCount = results.stream().filter(r -> r.placement == lastPlacement).count();
        // 2등과 꼴등이 따로 있을 때만 (3인 이상). 2인전·[1,2,2]는 2등이 곧 꼴등이라 제외.
        boolean transfer = secondCount > 0 && lastPlacement > 2;

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

            int placementGames = casual ? RatingConstants.PLACEMENT_GAMES : RatingConstants.LEGACY_PLACEMENT_GAMES;
            double kPlacement = casual ? RatingConstants.K_PLACEMENT : RatingConstants.LEGACY_K_PLACEMENT;
            double k = me.playCount < placementGames ? kPlacement : RatingConstants.K_REGULAR;
            double change = k * surprise / opponents;
            // 꼴등이 2등에게 K의 일부를 더 낸다. 동점자끼리 3인전에서 2등이 0점이 되는 걸 막는다.
            if (transfer && me.placement == 2) {
                change += k * RatingConstants.SECOND_PLACE_SHARE / secondCount;
            } else if (transfer && me.placement == lastPlacement) {
                change -= k * RatingConstants.SECOND_PLACE_SHARE / lastCount;
            }
            if (casual) change *= change > 0 ? winMultiplier(current) : lossMultiplier(current);
            double updated = current + change;
            // 하한 밑으로 끌어내리지 않는다. 이미 밑이면(방장이 낮게 설정) 더 내려가지만 않게 한다.
            double newRating = Math.max(updated, Math.min(current, RatingConstants.RATING_FLOOR));

            me.newStats = new GlickoStats(newRating, me.currentStats.getRatingDeviation(), 0.0);
        }
    }

    // 캐주얼 보정: 낮은 점수대는 이기면 더 받는다. CASUAL_CEILING부터는 그대로.
    private static double winMultiplier(double rating) {
        if (rating < 700) return 1.25;
        if (rating < RatingConstants.LOSS_HALF_BELOW) return 1.15;
        if (rating < RatingConstants.CASUAL_CEILING) return 1.05;
        return 1.0;
    }

    // 캐주얼 보정: 낮은 점수대는 지면 절반만 잃고, CASUAL_CEILING까지 점점 원래대로.
    private static double lossMultiplier(double rating) {
        if (rating < RatingConstants.LOSS_HALF_BELOW) return 0.5;
        if (rating >= RatingConstants.CASUAL_CEILING) return 1.0;
        double progress = (rating - RatingConstants.LOSS_HALF_BELOW)
            / (RatingConstants.CASUAL_CEILING - RatingConstants.LOSS_HALF_BELOW);
        return 0.5 + 0.5 * progress;
    }

    private static double expectedScore(double mine, double theirs) {
        return 1.0 / (1.0 + Math.pow(10.0, (theirs - mine) / RatingConstants.ELO_SCALE));
    }
}
