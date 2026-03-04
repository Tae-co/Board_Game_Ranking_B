package com.board_game_back.Utils;

public class MatchResult {
    private Rating player1;
    private Rating player2;
    private double score; // player1 기준의 점수 (1.0: 승, 0.5: 무, 0.0: 패)

    public MatchResult(Rating player1, Rating player2, double score) {
        this.player1 = player1;
        this.player2 = player2;
        this.score = score;
    }

    public Rating getPlayer1() { return player1; }
    public Rating getPlayer2() { return player2; }
    public double getScore() { return score; }
}
