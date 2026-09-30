package com.board_game_back.Entity;

import com.board_game_back.Utils.RatingConstants;
import jakarta.persistence.Embeddable;
import lombok.Getter;

@Embeddable
@Getter
public class GlickoStats {

    private double rating = RatingConstants.INITIAL_RATING;
    private double ratingDeviation = RatingConstants.INITIAL_DEVIATION;
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
        this.rating = RatingConstants.INITIAL_RATING;
        this.ratingDeviation = RatingConstants.INITIAL_DEVIATION;
        this.volatility = RatingConstants.INITIAL_VOLATILITY;
    }

    // rating이 곧 Elo 점수다. 0 미만은 0으로 clamp (음수 점수 노출 방지)
    public double getDisplayScore() {
        if (Double.isNaN(rating)) {
            return RatingConstants.INITIAL_RATING;
        }
        return Math.max(0.0, rating);
    }
}
