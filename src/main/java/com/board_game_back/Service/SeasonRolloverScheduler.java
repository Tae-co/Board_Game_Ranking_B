package com.board_game_back.Service;

import com.board_game_back.Entity.Community;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Utils.RegionTimeZones;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매시 정각에 커뮤니티를 훑어 "방금 달이 바뀐" 곳의 지난 시즌을 마감한다.
 *
 * <p><b>왜 매시 스캔인가:</b> 커뮤니티마다 타임존이 다른데 UTC 기준 1일 00:00에 한 번 돌면
 * 한국은 1일 09:00에 리셋돼 1일 새벽 경기가 지난 시즌으로 들어간다. region별 cron을 두면
 * 타임존 수만큼 cron이 늘어난다. 매시 스캔은 cron 하나로 전 타임존을 덮고,
 * 중복 실행은 {@link SeasonRolloverService}의 멱등성이 막는다.
 *
 * <p><b>과거 시즌은 소급해서 닫지 않는다.</b> 경계를 지난 뒤 {@link #ROLLOVER_GRACE} 안에만
 * 마감한다. 이게 없으면 배포 직후 첫 스캔에서 지난달이 통째로 마감돼 전 사용자 점수가
 * 예고 없이 리셋된다 (로컬에서 실제로 재현했다 — 기획 §16). 시즌제는 도입 시점부터
 * 앞으로만 적용되는 것이 맞고, 도입 전의 달을 시즌으로 만들어줄 이유가 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeasonRolloverScheduler {

    /**
     * 경계를 지난 뒤 이 시간 안에만 마감한다.
     *
     * <p>기획 원안은 "방금 1일 00:00을 지난 곳만"이었는데, 그대로 1시간으로 잡으면 마침 그
     * 시각에 서버가 내려가 있으면(재배포·장애) 그 달이 영영 안 닫혀 두 시즌이 합쳐진다.
     * 48시간이면 짧은 장애는 넘기고, 소급 마감도 일어나지 않는다.
     */
    private static final Duration ROLLOVER_GRACE = Duration.ofHours(48);

    private final CommunityRepository communityRepository;
    private final SeasonRolloverService rolloverService;

    @Scheduled(cron = "0 0 * * * *") // 매시 정각
    public void rolloverDueCommunities() {
        for (Community community : communityRepository.findAll()) {
            ZonedDateTime now = ZonedDateTime.now(RegionTimeZones.of(community.getRegion()));
            if (!isWithinRolloverWindow(now)) continue;

            YearMonth previousSeason = YearMonth.from(now).minusMonths(1);
            try {
                rolloverService.rollover(community.getId(), previousSeason);
            } catch (Exception e) {
                // 한 커뮤니티가 실패해도 나머지는 마감돼야 한다. 다음 정각에 다시 시도된다.
                log.error("시즌 롤오버 실패 community={} season={}", community.getId(), previousSeason, e);
            }
        }
    }

    /** 이 커뮤니티 타임존에서 방금(= {@link #ROLLOVER_GRACE} 안에) 시즌 경계를 지났는가. */
    static boolean isWithinRolloverWindow(ZonedDateTime now) {
        ZonedDateTime boundary = YearMonth.from(now).atDay(1).atStartOfDay(now.getZone());
        return Duration.between(boundary, now).compareTo(ROLLOVER_GRACE) <= 0;
    }
}
