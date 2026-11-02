package com.board_game_back.Service;

import com.board_game_back.Utils.RatingConstants;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 매장 Elo. {@link EloRatingCalculator}를 복사해서 시작했고 이후 규칙은 따로 간다 (plan-store §3).
 * 지금은 커뮤니티 캐주얼 규칙 그대로: 첫 3판 K 300·이후 200, 꼴등 → 2등 이전, 1300 미만 보정, 하한 500.
 */
@Component
public class StoreEloCalculator {

    /** rating·games는 이 판 전 값. 결과는 같은 순서의 새 점수 */
    public record Player(double rating, int games, int placement) {}

    public double[] calculate(List<Player> players) {
        int n = players.size();
        double[] result = new double[n];
        int opponents = n - 1;
        int lastPlacement = players.stream().mapToInt(Player::placement).max().orElse(0);
        long secondCount = players.stream().filter(p -> p.placement() == 2).count();
        long lastCount = players.stream().filter(p -> p.placement() == lastPlacement).count();
        // 2등과 꼴등이 따로 있을 때만 (3인 이상). 2인전·[1,2,2]는 2등이 곧 꼴등이라 제외.
        boolean transfer = secondCount > 0 && lastPlacement > 2;

        for (int i = 0; i < n; i++) {
            Player me = players.get(i);
            double current = me.rating();
            if (opponents < 1) {
                result[i] = current;
                continue;
            }
            double surprise = 0.0;
            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                Player other = players.get(j);
                double actual = me.placement() < other.placement() ? 1.0
                    : me.placement() == other.placement() ? 0.5 : 0.0;
                surprise += actual - expectedScore(current, other.rating());
            }
            double k = me.games() < RatingConstants.PLACEMENT_GAMES ? RatingConstants.K_PLACEMENT : RatingConstants.K_REGULAR;
            double change = k * surprise / opponents;
            if (transfer && me.placement() == 2) {
                change += k * RatingConstants.SECOND_PLACE_SHARE / secondCount;
            } else if (transfer && me.placement() == lastPlacement) {
                change -= k * RatingConstants.SECOND_PLACE_SHARE / lastCount;
            }
            change *= change > 0 ? winMultiplier(current) : lossMultiplier(current);
            double updated = current + change;
            result[i] = Math.max(updated, Math.min(current, RatingConstants.RATING_FLOOR));
        }
        return result;
    }

    private static double winMultiplier(double rating) {
        if (rating < 700) return 1.25;
        if (rating < RatingConstants.LOSS_HALF_BELOW) return 1.15;
        if (rating < RatingConstants.CASUAL_CEILING) return 1.05;
        return 1.0;
    }

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
