package com.board_game_back.Utils;

import java.util.ArrayList;
import java.util.List;

public class RatingCalculator {
    private double tau; // 시스템 상수 (일반적으로 0.3 ~ 1.2 사이, 0.5 권장)
    private double defaultVolatility;

    public RatingCalculator(double initVolatility, double tau) {
        this.defaultVolatility = initVolatility;
        this.tau = tau;
    }

    // Glicko-2 논문의 핵심 연산 실행
    public void updateRatings(RatingPeriodResults results) {
        for (Rating player : results.getParticipants()) {
            List<MatchResult> playerResults = new ArrayList<>();
            for (MatchResult r : results.getResults()) {
                if (r.getPlayer1().equals(player) || r.getPlayer2().equals(player)) {
                    playerResults.add(r);
                }
            }

            if (playerResults.isEmpty()) continue; // 게임을 안 한 사람은 스킵

            double v = variance(player, playerResults);
            double delta = delta(player, playerResults, v);
            double newVolatility = calculateNewVolatility(player, v, delta);

            player.setVolatility(newVolatility);

            double preRatingDeviation = Math.sqrt(Math.pow(player.getGlicko2RatingDeviation(), 2) + Math.pow(newVolatility, 2));
            double newRatingDeviation = 1 / Math.sqrt((1 / Math.pow(preRatingDeviation, 2)) + (1 / v));

            double newRating = player.getGlicko2Rating() + Math.pow(newRatingDeviation, 2) * sumExpectedScores(player, playerResults);

            player.setFinalRating(newRating);
            player.setFinalRatingDeviation(newRatingDeviation);
        }
    }

    private double g(double phi) {
        return 1.0 / Math.sqrt(1.0 + 3.0 * Math.pow(phi, 2) / Math.pow(Math.PI, 2));
    }

    private double E(double mu, double mu_j, double phi_j) {
        return 1.0 / (1.0 + Math.exp(-g(phi_j) * (mu - mu_j)));
    }

    private double variance(Rating player, List<MatchResult> results) {
        double v = 0.0;
        for (MatchResult r : results) {
            Rating opponent = r.getPlayer1().equals(player) ? r.getPlayer2() : r.getPlayer1();
            double E = E(player.getGlicko2Rating(), opponent.getGlicko2Rating(), opponent.getGlicko2RatingDeviation());
            v += Math.pow(g(opponent.getGlicko2RatingDeviation()), 2) * E * (1.0 - E);
        }
        return 1.0 / v;
    }

    private double sumExpectedScores(Rating player, List<MatchResult> results) {
        double sum = 0.0;
        for (MatchResult r : results) {
            Rating opponent = r.getPlayer1().equals(player) ? r.getPlayer2() : r.getPlayer1();
            double score = r.getPlayer1().equals(player) ? r.getScore() : 1.0 - r.getScore();
            sum += g(opponent.getGlicko2RatingDeviation()) * (score - E(player.getGlicko2Rating(), opponent.getGlicko2Rating(), opponent.getGlicko2RatingDeviation()));
        }
        return sum;
    }

    private double delta(Rating player, List<MatchResult> results, double v) {
        return v * sumExpectedScores(player, results);
    }

    private double calculateNewVolatility(Rating player, double v, double delta) {
        double a = Math.log(Math.pow(player.getVolatility(), 2));
        double phi = player.getGlicko2RatingDeviation();
        double epsilon = 0.000001;

        double A = a;
        double B = 0.0;

        if (Math.pow(delta, 2) > Math.pow(phi, 2) + v) {
            B = Math.log(Math.pow(delta, 2) - Math.pow(phi, 2) - v);
        } else {
            double k = 1;
            while (f(a - k * tau, delta, phi, v, a) < 0) {
                k++;
            }
            B = a - k * tau;
        }

        double fA = f(A, delta, phi, v, a);
        double fB = f(B, delta, phi, v, a);

        while (Math.abs(B - A) > epsilon) {
            double C = A + (A - B) * fA / (fB - fA);
            double fC = f(C, delta, phi, v, a);
            if (fC * fB < 0) {
                A = B; fA = fB;
            } else {
                fA = fA / 2.0;
            }
            B = C; fB = fC;
        }
        return Math.exp(A / 2.0);
    }

    private double f(double x, double delta, double phi, double v, double a) {
        double ex = Math.exp(x);
        double num = ex * (Math.pow(delta, 2) - Math.pow(phi, 2) - v - ex);
        double den = 2.0 * Math.pow(Math.pow(phi, 2) + v + ex, 2);
        return (num / den) - ((x - a) / Math.pow(tau, 2));
    }

}
