package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.board_game_back.Entity.GlickoStats;
import com.board_game_back.Utils.RatingConstants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EloRatingCalculatorTest {

    private final EloRatingCalculator calculator = new EloRatingCalculator();

    @Test
    void 동점자_4인전_배치고사는_1등_플러스_250_꼴등_마이너스_250() {
        List<RatingCalculator.PlayerResult> results = players(0, 1000, 1000, 1000, 1000);

        calculator.calculateMultiplayerRatings(results);

        // 기본 +250, +83.3, -83.3, -250에 꼴등 → 2등 50 이전
        assertThat(changes(results)).containsExactly(250.0, 133.3, -83.3, -300.0);
    }

    @Test
    void 동점자_3인전에서_2등도_점수를_받고_꼴등이_그만큼_더_잃는다() {
        List<RatingCalculator.PlayerResult> results = players(0, 1000, 1000, 1000);

        calculator.calculateMultiplayerRatings(results);

        assertThat(changes(results)).containsExactly(250.0, 50.0, -300.0);
    }

    @Test
    void 배치고사_이후엔_2등_몫도_줄어든다() {
        List<RatingCalculator.PlayerResult> results = players(RatingConstants.PLACEMENT_GAMES, 1000, 1000, 1000);

        calculator.calculateMultiplayerRatings(results);

        assertThat(changes(results)).containsExactly(100.0, 20.0, -120.0);
    }

    @Test
    void 이인전은_2등이_곧_꼴등이라_이전이_없다() {
        List<RatingCalculator.PlayerResult> results = players(0, 1000, 1000);

        calculator.calculateMultiplayerRatings(results);

        assertThat(changes(results)).containsExactly(250.0, -250.0);
    }

    @Test
    void 공동_꼴등이면_나눠서_낸다() {
        List<RatingCalculator.PlayerResult> results = new ArrayList<>();
        results.add(new RatingCalculator.PlayerResult(1L, 1, 0, new GlickoStats(1000, 0, 0)));
        results.add(new RatingCalculator.PlayerResult(2L, 2, 0, new GlickoStats(1000, 0, 0)));
        results.add(new RatingCalculator.PlayerResult(3L, 3, 0, new GlickoStats(1000, 0, 0)));
        results.add(new RatingCalculator.PlayerResult(4L, 3, 0, new GlickoStats(1000, 0, 0)));

        calculator.calculateMultiplayerRatings(results);

        assertThat(changes(results).stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(0.0, within(0.5));
        assertThat(changes(results).get(2)).isEqualTo(changes(results).get(3));
    }

    @Test
    void 배치고사_이후엔_변동폭이_줄어든다() {
        List<RatingCalculator.PlayerResult> results = players(RatingConstants.PLACEMENT_GAMES, 1000, 1000);

        calculator.calculateMultiplayerRatings(results);

        assertThat(changes(results)).containsExactly(100.0, -100.0);
    }

    @Test
    void 하한_위에서는_오른_만큼_누군가_내려간다() {
        List<RatingCalculator.PlayerResult> results = players(0, 900, 1500, 1200, 2000);

        calculator.calculateMultiplayerRatings(results);

        assertThat(changes(results).stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(0.0, within(0.5));
    }

    @Test
    void 약자가_이기면_강자가_이길_때보다_많이_오른다() {
        List<RatingCalculator.PlayerResult> upset = players(0, 500, 1500);
        List<RatingCalculator.PlayerResult> expected = players(0, 1500, 500);

        calculator.calculateMultiplayerRatings(upset);
        calculator.calculateMultiplayerRatings(expected);

        assertThat(changes(upset).get(0)).isGreaterThan(250.0);
        assertThat(changes(expected).get(0)).isLessThan(250.0);
    }

    @Test
    void 지면_점수가_내려가되_하한_500_밑으로는_안_내려간다() {
        List<RatingCalculator.PlayerResult> results = players(0, 1000, 600);

        calculator.calculateMultiplayerRatings(results);

        assertThat(results.get(1).newStats.getRating()).isEqualTo(RatingConstants.RATING_FLOOR);
    }

    @Test
    void 이미_하한_밑이면_이겼을_땐_오르고_졌을_땐_그대로다() {
        List<RatingCalculator.PlayerResult> win = players(0, 300, 1000);
        List<RatingCalculator.PlayerResult> lose = players(0, 1000, 300);

        calculator.calculateMultiplayerRatings(win);
        calculator.calculateMultiplayerRatings(lose);

        assertThat(win.get(0).newStats.getRating()).isGreaterThan(300.0);
        assertThat(lose.get(1).newStats.getRating()).isEqualTo(300.0);
    }

    @Test
    void 동순위는_반반으로_나눈다() {
        List<RatingCalculator.PlayerResult> results = new ArrayList<>();
        results.add(new RatingCalculator.PlayerResult(1L, 1, 0, new GlickoStats(1000, 0, 0)));
        results.add(new RatingCalculator.PlayerResult(2L, 1, 0, new GlickoStats(1000, 0, 0)));

        calculator.calculateMultiplayerRatings(results);

        assertThat(changes(results)).containsExactly(0.0, 0.0);
    }

    /** 점수 순서대로 1등, 2등, ... */
    private List<RatingCalculator.PlayerResult> players(int playCount, double... scores) {
        List<RatingCalculator.PlayerResult> results = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            results.add(new RatingCalculator.PlayerResult(
                (long) i, i + 1, playCount, new GlickoStats(scores[i], 0, 0)));
        }
        return results;
    }

    /** 소수 첫째 자리까지 반올림한 변화량 */
    private List<Double> changes(List<RatingCalculator.PlayerResult> results) {
        return results.stream()
            .map(r -> Math.round((r.newStats.getRating() - r.currentStats.getRating()) * 10) / 10.0)
            .toList();
    }
}
