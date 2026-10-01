package com.board_game_back.Service;

import com.board_game_back.Entity.RoomSeason;
import com.board_game_back.Repository.RoomSeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매시 정각에 종료 시각이 지난 방 시즌을 마감한다.
 *
 * <p>종료 시각은 호스트가 정한 종료일 다음 날 00:00(커뮤니티 타임존)이라 대부분 정각에 걸린다.
 * 월간 시즌 때의 48시간 grace(§5)는 없다 — 시즌이 명시적으로 저장되므로 늦게 돌아도
 * "도입 전 기간을 소급 마감"하는 일이 생기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeasonRolloverScheduler {

    private final RoomSeasonRepository seasonRepository;
    private final SeasonRolloverService rolloverService;

    @Scheduled(cron = "0 0 * * * *") // 매시 정각
    public int rolloverDueSeasons() {
        int rolled = 0;
        for (RoomSeason season : seasonRepository.findDue(SeasonBoundaryService.nowUtc())) {
            try {
                rolloverService.rollover(season.getId());
                rolled++;
            } catch (Exception e) {
                // 한 방이 실패해도 나머지는 마감돼야 한다. 다음 정각에 다시 시도된다.
                log.error("시즌 롤오버 실패 room={} season={}", season.getRoomId(), season.getId(), e);
            }
        }
        return rolled;
    }
}
