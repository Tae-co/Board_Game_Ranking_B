package com.board_game_back.Service;

import com.board_game_back.Entity.GlickoStats;
import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.IPlayer;
import de.gesundkrank.jskills.ITeam;
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Rating;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.TrueSkillCalculator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class Glicko2Calculator {

    // μ₀=25, σ₀=25/3, β=25/6, τ=25/300, drawProbability=0
    private static final GameInfo GAME_INFO =
        new GameInfo(25.0, 25.0 / 3, 25.0 / 6, 25.0 / 300, 0.0);

    public void calculateMultiplayerRatings(List<PlayerResult> results) {
        // 등수 오름차순 정렬 (jskills는 1등 팀부터)
        List<PlayerResult> sorted = results.stream()
            .sorted(Comparator.comparingInt(r -> r.placement))
            .collect(Collectors.toList());

        List<ITeam> teams = new ArrayList<>();
        int[] ranks = new int[sorted.size()];

        for (int i = 0; i < sorted.size(); i++) {
            PlayerResult pr = sorted.get(i);
            Rating tsRating = new Rating(
                pr.currentStats.getRating(),
                pr.currentStats.getRatingDeviation()
            );
            Team team = new Team(new Player<>(pr.memberId), tsRating);
            teams.add(team);
            ranks[i] = pr.placement; // 동점 → 같은 숫자 = 무승부 처리
        }

        Map<IPlayer, Rating> newRatings =
            TrueSkillCalculator.calculateNewRatings(GAME_INFO, teams, ranks);

        // memberId → 새 Rating 매핑
        Map<Long, Rating> resultMap = new HashMap<>();
        for (Map.Entry<IPlayer, Rating> entry : newRatings.entrySet()) {
            @SuppressWarnings("unchecked")
            Long memberId = ((Player<Long>) entry.getKey()).getId();
            resultMap.put(memberId, entry.getValue());
        }

        for (PlayerResult pr : results) {
            Rating r = resultMap.get(pr.memberId);
            pr.newStats = new GlickoStats(r.getMean(), r.getStandardDeviation(), 0.0);
        }
    }

    public static class PlayerResult {

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
