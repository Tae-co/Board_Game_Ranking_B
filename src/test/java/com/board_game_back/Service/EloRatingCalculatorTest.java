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

    // 아래 테스트들은 캐주얼 보정이 없는 CASUAL_CEILING(1300) 이상에서 기본 Elo만 본다.

    @Test
    void 동점자_4인전_배치고사는_1등_플러스_150_꼴등_마이너스_150() {
        List<RatingCalculator.PlayerResult> results = players(0, 1500, 1500, 1500, 1500);

        calculator.calculateMultiplayerRatings(results);

        // 기본 +150, +50, -50, -150에 꼴등 → 2등 30 이전
        assertThat(changes(results)).containsExactly(150.0, 80.0, -50.0, -180.0);
    }

    @Test
    void 동점자_3인전에서_2등도_점수를_받고_꼴등이_그만큼_더_잃는다() {
        List<RatingCalculator.PlayerResult> results = players(0, 1500, 1500, 1500);

        calculator.calculateMultiplayerRatings(results);

        assertThat(changes(results)).containsExactly(150.0, 30.0, -180.0);
    }

    @Test
    void 배치고사_이후엔_2등_몫도_줄어든다() {
        List<RatingCalculator.PlayerResult> results = players(RatingConstants.PLACEMENT_GAMES, 1500, 1500, 1500);

        calculator.calculateMultiplayerRatings(results);

        assertThat(changes(results)).containsExactly(100.0, 20.0, -120.0);
    }

    @Test
    void 이인전은_2등이_곧_꼴등이라_이전이_없다() {
        List<RatingCalculator.PlayerResult> results = players(0, 1500, 1500);

        calculator.calculateMultiplayerRatings(results);

        assertThat(changes(results)).containsExactly(150.0, -150.0);
    }

    @Test
    void 공동_꼴등이면_나눠서_낸다() {
        List<RatingCalculator.PlayerResult> results = new ArrayList<>();
        results.add(new RatingCalculator.PlayerResult(1L, 1, 0, new GlickoStats(1500, 0, 0)));
        results.add(new RatingCalculator.PlayerResult(2L, 2, 0, new GlickoStats(1500, 0, 0)));
        results.add(new RatingCalculator.PlayerResult(3L, 3, 0, new GlickoStats(1500, 0, 0)));
        results.add(new RatingCalculator.PlayerResult(4L, 3, 0, new GlickoStats(1500, 0, 0)));

        calculator.calculateMultiplayerRatings(results);

        assertThat(changes(results).stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(0.0, within(0.5));
        assertThat(changes(results).get(2)).isEqualTo(changes(results).get(3));
    }

    @Test
    void 배치고사_이후엔_변동폭이_줄어든다() {
        List<RatingCalculator.PlayerResult> results = players(RatingConstants.PLACEMENT_GAMES, 1500, 1500);

        calculator.calculateMultiplayerRatings(results);

        assertThat(changes(results)).containsExactly(100.0, -100.0);
    }

    @Test
    void 보정이_없는_구간에서는_오른_만큼_누군가_내려간다() {
        List<RatingCalculator.PlayerResult> results = players(0, 1300, 1900, 1600, 2400);

        calculator.calculateMultiplayerRatings(results);

        assertThat(changes(results).stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(0.0, within(0.5));
    }

    @Test
    void 약자가_이기면_강자가_이길_때보다_많이_오른다() {
        List<RatingCalculator.PlayerResult> upset = players(0, 1500, 2500);
        List<RatingCalculator.PlayerResult> expected = players(0, 2500, 1500);

        calculator.calculateMultiplayerRatings(upset);
        calculator.calculateMultiplayerRatings(expected);

        assertThat(changes(upset).get(0)).isGreaterThan(150.0);
        assertThat(changes(expected).get(0)).isLessThan(150.0);
    }

    @Test
    void 지면_점수가_내려가되_하한_500_밑으로는_안_내려간다() {
        List<RatingCalculator.PlayerResult> results = players(0, 1000, 550); // 절반만 잃어도 -63

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

    @Test
    void 낮은_점수대는_이기면_더_받고_지면_절반만_잃는다() {
        List<RatingCalculator.PlayerResult> results = players(0, 600, 600);

        calculator.calculateMultiplayerRatings(results);

        // 기본 ±150 → 승리 ×1.25, 패배 ×0.5
        assertThat(changes(results)).containsExactly(187.5, -75.0);
    }

    @Test
    void 천에서_천삼백_사이는_감점이_점점_원래대로_돌아온다() {
        List<RatingCalculator.PlayerResult> results = players(0, 1150, 1150);

        calculator.calculateMultiplayerRatings(results);

        // 승리 ×1.05, 패배는 1000(×0.5)과 1300(×1.0)의 중간 ×0.75
        assertThat(changes(results)).containsExactly(157.5, -112.5);
    }

    @Test
    void 배치고사_2연승해도_1000에_닿지_않는다() {
        List<RatingCalculator.PlayerResult> first = players(0, 500, 500, 500, 500);
        calculator.calculateMultiplayerRatings(first);
        double afterFirst = first.get(0).newStats.getRating();

        List<RatingCalculator.PlayerResult> second = players(1, afterFirst, 500, 500, 500);
        calculator.calculateMultiplayerRatings(second);

        assertThat(second.get(0).newStats.getRating()).isLessThan(1000.0);
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
