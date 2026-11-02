package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.board_game_back.Entity.GlickoStats;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 매장 Elo는 지금 커뮤니티 캐주얼 규칙과 같은 값을 내야 한다 (복사로 시작, plan-store §3) */
class StoreEloCalculatorTest {

    private final StoreEloCalculator store = new StoreEloCalculator();
    private final EloRatingCalculator community = new EloRatingCalculator();

    @Test
    void 커뮤니티_캐주얼_규칙과_같은_값() {
        double[][] cases = {
            {500, 0, 1, 500, 0, 2},
            {1200, 5, 2, 900, 1, 1},
            {1500, 10, 1, 1400, 10, 2},
        };
        for (double[] c : cases) {
            assertSame(List.of(new StoreEloCalculator.Player(c[0], (int) c[1], (int) c[2]),
                new StoreEloCalculator.Player(c[3], (int) c[4], (int) c[5])));
        }
        assertSame(List.of(
            new StoreEloCalculator.Player(800, 4, 1),
            new StoreEloCalculator.Player(800, 4, 2),
            new StoreEloCalculator.Player(800, 4, 3),
            new StoreEloCalculator.Player(800, 4, 4)));
    }

    @Test
    void 하한_500_밑으로_안_내려간다() {
        double[] r = store.calculate(List.of(
            new StoreEloCalculator.Player(500, 0, 1),
            new StoreEloCalculator.Player(500, 0, 2)));
        assertThat(r[0]).isGreaterThan(500);
        assertThat(r[1]).isEqualTo(500);
    }

    private void assertSame(List<StoreEloCalculator.Player> players) {
        double[] mine = store.calculate(players);
        List<RatingCalculator.PlayerResult> results = players.stream().map(p -> {
            return new RatingCalculator.PlayerResult(null, p.placement(), p.games(), new GlickoStats(p.rating(), 0.0, 0.0));
        }).toList();
        community.calculateMultiplayerRatings(results, true);
        for (int i = 0; i < players.size(); i++) {
            assertThat(mine[i]).isCloseTo(results.get(i).newStats.getRating(), within(1e-9));
        }
    }
}
