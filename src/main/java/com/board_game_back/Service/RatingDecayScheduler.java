package com.board_game_back.Service;

import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Utils.RatingConstants;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RatingDecayScheduler {

    // 표시 점수 -10 = μ - (10 / DISPLAY_SCALE) = μ - 0.2
    private static final double DECAY_MU = 10.0 / RatingConstants.DISPLAY_SCALE;

    private final PlayerGameRatingRepository ratingRepository;

    @Scheduled(cron = "0 0 0 * * MON") // 매주 월요일 자정
    @Transactional
    public void applyWeeklyDecay() {
        LocalDateTime cutoff = LocalDateTime.now().minusWeeks(1);
        List<PlayerGameRating> staleRatings = ratingRepository.findStaleActiveRatings(cutoff);
        for (PlayerGameRating rating : staleRatings) {
            rating.applyDecay(DECAY_MU);
        }
        ratingRepository.saveAll(staleRatings);
    }
}
