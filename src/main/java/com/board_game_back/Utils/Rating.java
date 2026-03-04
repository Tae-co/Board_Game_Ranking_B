package com.board_game_back.Utils;

public class Rating {
    private String uid;
    private double rating;
    private double ratingDeviation;
    private double volatility;
    private int numberOfResults;

    public Rating(String uid, RatingCalculator calculator, double initRating, double initRatingDeviation, double initVolatility) {
        this.uid = uid;
        this.rating = initRating;
        this.ratingDeviation = initRatingDeviation;
        this.volatility = initVolatility;
        this.numberOfResults = 0;
    }

    // Glicko-2 내부 수식용 스케일 변환 (기존 점수 - 1500 / 173.7178)
    public double getGlicko2Rating() { return (this.rating - 1500) / 173.7178; }
    public double getGlicko2RatingDeviation() { return this.ratingDeviation / 173.7178; }

    // 계산 완료 후 원래 스케일로 복귀
    public void setFinalRating(double glicko2Rating) { this.rating = (glicko2Rating * 173.7178) + 1500; }
    public void setFinalRatingDeviation(double glicko2RatingDeviation) { this.ratingDeviation = glicko2RatingDeviation * 173.7178; }

    public String getUid() { return uid; }
    public double getRating() { return rating; }
    public double getRatingDeviation() { return ratingDeviation; }
    public double getVolatility() { return volatility; }
    public void setVolatility(double volatility) { this.volatility = volatility; }

    public void incrementResults() { this.numberOfResults++; }
    public int getNumberOfResults() { return numberOfResults; }

}
