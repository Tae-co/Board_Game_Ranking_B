package com.board_game_back.Entity;

import com.board_game_back.Utils.RatingConstants;
import jakarta.persistence.Embeddable;
import lombok.Getter;

@Embeddable
@Getter
public class GlickoStats {

    private double rating = RatingConstants.INITIAL_MU;
    private double ratingDeviation = RatingConstants.INITIAL_SIGMA;
    private double volatility = RatingConstants.INITIAL_VOLATILITY;

    protected GlickoStats() {}

    public GlickoStats(double rating, double ratingDeviation, double volatility) {
        this.rating = rating;
        this.ratingDeviation = ratingDeviation;
        this.volatility = volatility;
    }

    public void update(double rating, double ratingDeviation, double volatility) {
        this.rating = rating;
        this.ratingDeviation = ratingDeviation;
        this.volatility = volatility;
    }

    public void reset() {
        this.rating = RatingConstants.INITIAL_MU;
        this.ratingDeviation = RatingConstants.INITIAL_SIGMA;
        this.volatility = RatingConstants.INITIAL_VOLATILITY;
    }

    // (μ - 3σ) × 50 + 500 — 신규=500, 0 미만은 0으로 clamp (음수 점수 노출 방지)
    public double getDisplayScore() {
        double score = (rating - RatingConstants.DISPLAY_SIGMA_FACTOR * ratingDeviation)
            * RatingConstants.DISPLAY_SCALE + RatingConstants.DISPLAY_OFFSET;
        if (Double.isNaN(score)) {
            return RatingConstants.DISPLAY_OFFSET;
        }
        return Math.max(0.0, score);
    }
}
