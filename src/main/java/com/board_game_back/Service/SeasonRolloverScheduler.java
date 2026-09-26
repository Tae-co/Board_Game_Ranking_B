package com.board_game_back.Service;

import com.board_game_back.Entity.Community;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Utils.RegionTimeZones;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매시 정각에 커뮤니티를 훑어 "자기 타임존에서 달이 바뀐" 곳의 지난 시즌을 마감한다.
 *
 * <p><b>왜 매시 스캔인가:</b> 커뮤니티마다 타임존이 다른데 UTC 기준 1일 00:00에 한 번 돌면
 * 한국은 1일 09:00에 리셋돼 1일 새벽 경기가 지난 시즌으로 들어간다. region별 cron을 두면
 * 타임존 수만큼 cron이 늘어난다. 매시 스캔은 cron 하나로 전 타임존을 덮고,
 * 중복 실행은 {@link SeasonRolloverService}의 멱등성이 막는다.
 *
 * <p><b>⚠️ 배포 전 주의:</b> 이 스케줄러가 처음 도는 순간 모든 방의 지난달이 곧바로 마감된다.
 * 예고 없이 점수가 리셋되므로 기획 §11(예고 배너 + 시즌 0 스냅샷, Phase 7)이 먼저 올라가야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeasonRolloverScheduler {

    private final CommunityRepository communityRepository;
    private final SeasonRolloverService rolloverService;

    @Scheduled(cron = "0 0 * * * *") // 매시 정각
    public void rolloverDueCommunities() {
        for (Community community : communityRepository.findAll()) {
            ZoneId zone = RegionTimeZones.of(community.getRegion());
            YearMonth previousSeason = YearMonth.from(ZonedDateTime.now(zone)).minusMonths(1);
            try {
                rolloverService.rollover(community.getId(), previousSeason);
            } catch (Exception e) {
                // 한 커뮤니티가 실패해도 나머지는 마감돼야 한다. 다음 정각에 다시 시도된다.
                log.error("시즌 롤오버 실패 community={} season={}", community.getId(), previousSeason, e);
            }
        }
    }
}
