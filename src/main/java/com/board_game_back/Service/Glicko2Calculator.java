package com.board_game_back.Service;

import com.board_game_back.Entity.GlickoStats;
import com.board_game_back.Utils.Rating;
import com.board_game_back.Utils.RatingCalculator;
import com.board_game_back.Utils.RatingPeriodResults;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class Glicko2Calculator {

    // 다인원 매치 결과를 받아 각 플레이어의 새로운 스탯을 계산
    public void calculateMultiplayerRatings(List<PlayerResult> results) {

        // 1. Glicko-2 수학 계산기 세팅 (변동성 0.06, 시스템 상수 Tau 0.5가 기본값입니다)
        RatingCalculator calculator = new RatingCalculator(0.06, 0.5);
        RatingPeriodResults engineResults = new RatingPeriodResults();

        // 2. 우리 DB의 GlickoStats 데이터를 라이브러리 전용 'Rating' 객체로 변환
        Map<Long, Rating> ratingMap = new HashMap<>();

        for (PlayerResult pr : results) {
            // (이름, 계산기, 현재 Rating, 현재 RD, 현재 Volatility)
            Rating rating = new Rating(
                pr.memberId.toString(),
                calculator,
                pr.currentStats.getRating(),
                pr.currentStats.getRatingDeviation(),
                pr.currentStats.getVolatility()
            );
            ratingMap.put(pr.memberId, rating);
        }

        // 3. 모든 플레이어를 서로 1대1 매칭시켜서 라이브러리 결과 바구니(engineResults)에 담기
        for (int i = 0; i < results.size(); i++) {
            for (int j = i + 1; j < results.size(); j++) {
                PlayerResult p1 = results.get(i);
                PlayerResult p2 = results.get(j);

                Rating rating1 = ratingMap.get(p1.memberId);
                Rating rating2 = ratingMap.get(p2.memberId);

                // 등수를 비교하여 라이브러리에 승/무/패 입력
                if (p1.placement < p2.placement) {
                    engineResults.addResult(rating1, rating2); // p1 승리
                } else if (p1.placement > p2.placement) {
                    engineResults.addResult(rating2, rating1); // p2 승리
                } else {
                    engineResults.addDraw(rating1, rating2);   // 동점 (무승부)
                }
            }
        }

        // 4. 🚀 수학 엔진 가동! (알아서 복잡한 공식을 돌려 점수를 재계산함)
        calculator.updateRatings(engineResults);

        // 5. 계산이 끝난 새로운 점수를 다시 우리의 DTO(PlayerResult)에 저장
        for (PlayerResult pr : results) {
            Rating updatedRating = ratingMap.get(pr.memberId);

            pr.newStats = new GlickoStats(
                updatedRating.getRating(),
                updatedRating.getRatingDeviation(),
                updatedRating.getVolatility()
            );
        }
    }

    // 계산을 위해 임시로 사용할 내부 DTO
    public static class PlayerResult {

        public Long memberId;
        public int placement;
        public GlickoStats currentStats; // 현재 스탯
        public GlickoStats newStats;     // 계산 완료 후 적용될 스탯

        public PlayerResult(Long memberId, int placement, GlickoStats currentStats) {
            this.memberId = memberId;
            this.placement = placement;
            this.currentStats = currentStats;
        }
    }
}
