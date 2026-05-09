package com.board_game_back.Entity;

import jakarta.persistence.Embeddable;
import lombok.Getter;

@Embeddable
@Getter
public class GlickoStats {

    private double rating = 25.0;                   // μ (TrueSkill mean)
    private double ratingDeviation = 25.0 / 3;     // σ (TrueSkill std deviation ≈ 8.333)
    private double volatility = 0.0;               // TrueSkill 미사용

    protected GlickoStats() {
    } // JPA 기본 생성자 @NoArgsConstructor(access = AccessLevel.PROTECTED)와 같은역할

    public GlickoStats(double rating, double ratingDeviation, double volatility) {
        this.rating = rating;
        this.ratingDeviation = ratingDeviation;
        this.volatility = volatility;
    }

    // 값 업데이트 메서드
    public void update(double rating, double ratingDeviation, double volatility) {
        this.rating = rating;
        this.ratingDeviation = ratingDeviation;
        this.volatility = volatility;
    }

    // 초기값으로 리셋
    public void reset() {
        this.rating = 25.0;
        this.ratingDeviation = 25.0 / 3;
        this.volatility = 0.0;
    }

    // (μ - 3σ) × 50 + 1500 — 신규=1500, 범위 약 1250~2800
    public double getDisplayScore() {
        return (rating - 3 * ratingDeviation) * 50 + 1500;
    }

}
