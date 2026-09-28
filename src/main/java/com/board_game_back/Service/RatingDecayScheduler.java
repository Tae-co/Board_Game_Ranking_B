package com.board_game_back.Service;

import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Repository.MatchParticipantRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Utils.RatingConstants;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비활동 decay. 기획: {@code docs/plans/plan-season-reset.md} §7.
 *
 * <p><b>왜 7일인가:</b> 시즌이 최대 31일인데 기준이 28일이면 시즌당 많아야 한 번, 그것도
 * 항상 리셋 직전에 발동한다. 이틀 뒤 리셋에 지워질 하락은 설명할 수 없는 노이즈다.
 * 7일이면 시즌 안에서 4번 발동해 실제 의미가 생긴다.
 *
 * <p><b>왜 고정값이 아닌가:</b> 전원 −30/주 같은 고정값을 쓰면 1승에 +457 버는 신규와
 * +21 버는 고인물이 같은 폭으로 깎인다. 각자의 승리 평균에 비례시키면 "일주일 빠지면
 * 지난 승리 하나가 거의 날아간다"가 누구에게나 같은 무게로 성립한다.
 *
 * <p><b>하한은 표시 점수 500</b>({@link PlayerGameRating#applyDecay}). 운영 데이터 기준
 * 주당 감소 중앙값이 315점이라 하한이 없으면 월 1회 참석자가 3주 만에 0점이 된다.
 */
@Component
@RequiredArgsConstructor
public class RatingDecayScheduler {

    /** 이만큼 안 플레이하면 깎는다. */
    private static final int STALE_DAYS = 7;

    /** 승리 평균의 이 비율만큼 깎는다. */
    private static final double DECAY_RATIO = 0.8;

    private final PlayerGameRatingRepository ratingRepository;
    private final MatchParticipantRepository participantRepository;

    @Scheduled(cron = "0 0 0 * * MON") // 매주 월요일 자정
    @Transactional
    public void applyWeeklyDecay() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(STALE_DAYS);
        List<PlayerGameRating> staleRatings = ratingRepository.findStaleActiveRatings(cutoff);
        if (staleRatings.isEmpty()) return;

        Map<PlayerKey, Double> winAvgByPlayer = new HashMap<>();
        for (Object[] row : participantRepository.findWinRatingChangeAvgByPlayer()) {
            winAvgByPlayer.put(new PlayerKey((Long) row[0], (Long) row[1], (Long) row[2]), (Double) row[3]);
        }
        Map<RoomGameKey, Double> winAvgByRoomGame = new HashMap<>();
        for (Object[] row : participantRepository.findWinRatingChangeAvgByRoomGame()) {
            winAvgByRoomGame.put(new RoomGameKey((Long) row[0], (Long) row[1]), (Double) row[2]);
        }

        for (PlayerGameRating rating : staleRatings) {
            double displayDecay = displayDecayOf(rating, winAvgByPlayer, winAvgByRoomGame);
            if (displayDecay <= 0) continue;
            rating.applyDecay(displayDecay / RatingConstants.DISPLAY_SCALE);
        }
        ratingRepository.saveAll(staleRatings);
    }

    /** 깎을 표시 점수. 근거가 될 승리 표본이 아예 없으면 0 — 근거 없이 깎지 않는다. */
    private double displayDecayOf(
        PlayerGameRating rating,
        Map<PlayerKey, Double> winAvgByPlayer,
        Map<RoomGameKey, Double> winAvgByRoomGame) {

        if (rating.getRoom() == null || rating.getBoardGame() == null || rating.getMember() == null) {
            return 0.0;
        }
        Long roomId = rating.getRoom().getId();
        Long boardGameId = rating.getBoardGame().getId();

        Double basis = winAvgByPlayer.get(new PlayerKey(roomId, boardGameId, rating.getMember().getId()));
        // 승리 0회면 같은 방·게임의 승리 평균을 쓴다.
        if (basis == null) basis = winAvgByRoomGame.get(new RoomGameKey(roomId, boardGameId));
        if (basis == null) return 0.0;

        return basis * DECAY_RATIO;
    }

    private record PlayerKey(Long roomId, Long boardGameId, Long memberId) {}

    private record RoomGameKey(Long roomId, Long boardGameId) {}
}
