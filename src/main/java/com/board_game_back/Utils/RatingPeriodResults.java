package com.board_game_back.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RatingPeriodResults {
    private List<MatchResult> results = new ArrayList<>();
    private Set<Rating> participants = new HashSet<>();

    public void addResult(Rating winner, Rating loser) {
        results.add(new MatchResult(winner, loser, 1.0));
        participants.add(winner);
        participants.add(loser);
    }

    public void addDraw(Rating player1, Rating player2) {
        results.add(new MatchResult(player1, player2, 0.5));
        participants.add(player1);
        participants.add(player2);
    }

    public List<MatchResult> getResults() { return results; }
    public Set<Rating> getParticipants() { return participants; }

}
