package com.board_game_back.Entity;

import jakarta.persistence.Embeddable;
import lombok.Getter;

@Embeddable
@Getter
public class GlickoStats {

    private double rating = 1500.0;          // 기본 레이팅
    private double ratingDeviation = 350.0;  // 기본 RD (초기에는 신뢰도가 낮으므로 높게 설정)
    private double volatility = 0.06;        // 기본 변동성

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
        this.rating = 1500.0;
        this.ratingDeviation = 350.0;
        this.volatility = 0.06;
    }

}
