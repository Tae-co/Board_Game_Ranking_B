package com.board_game_back.Service;

import com.board_game_back.Entity.GlickoStats;
import com.board_game_back.Utils.RatingConstants;
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
public class TrueSkillRatingCalculator implements RatingCalculator {

    private static final GameInfo GAME_INFO = new GameInfo(
        RatingConstants.INITIAL_MU,
        RatingConstants.INITIAL_SIGMA,
        RatingConstants.BETA,
        RatingConstants.DYNAMICS,
        RatingConstants.DRAW_PROBABILITY
    );

    @Override
    public void calculateMultiplayerRatings(List<RatingCalculator.PlayerResult> results) {
        List<RatingCalculator.PlayerResult> sorted = results.stream()
            .sorted(Comparator.comparingInt(r -> r.placement))
            .collect(Collectors.toList());

        List<ITeam> teams = new ArrayList<>();
        int[] ranks = new int[sorted.size()];

        for (int i = 0; i < sorted.size(); i++) {
            RatingCalculator.PlayerResult pr = sorted.get(i);
            Rating tsRating = new Rating(
                pr.currentStats.getRating(),
                pr.currentStats.getRatingDeviation()
            );
            Team team = new Team(new Player<>(pr.memberId), tsRating);
            teams.add(team);
            ranks[i] = pr.placement;
        }

        Map<IPlayer, Rating> newRatings =
            TrueSkillCalculator.calculateNewRatings(GAME_INFO, teams, ranks);

        Map<Long, Rating> resultMap = new HashMap<>();
        for (Map.Entry<IPlayer, Rating> entry : newRatings.entrySet()) {
            @SuppressWarnings("unchecked")
            Long memberId = ((Player<Long>) entry.getKey()).getId();
            resultMap.put(memberId, entry.getValue());
        }

        for (RatingCalculator.PlayerResult pr : results) {
            Rating r = resultMap.get(pr.memberId);
            pr.newStats = new GlickoStats(r.getMean(), r.getStandardDeviation(), 0.0);
        }
    }
}
