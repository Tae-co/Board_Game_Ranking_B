package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/**
 * 롤오버 창 판정. 시즌제는 도입 시점부터 앞으로만 적용되고 과거 달을 소급해서 닫지 않는다.
 */
class SeasonRolloverSchedulerTest {

    private static final ZoneId 서울 = ZoneId.of("Asia/Seoul");

    @Test
    void 경계_직후는_마감한다() {
        ZonedDateTime 십월_일일_영시 = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, 서울);

        assertThat(SeasonRolloverScheduler.isWithinRolloverWindow(십월_일일_영시)).isTrue();
    }

    @Test
    void 경계_후_47시간은_아직_마감한다() {
        // 재배포·장애로 경계 시각에 서버가 내려가 있었더라도 놓치지 않는다
        ZonedDateTime 십월_이일_이십삼시 = ZonedDateTime.of(2026, 10, 2, 23, 0, 0, 0, 서울);

        assertThat(SeasonRolloverScheduler.isWithinRolloverWindow(십월_이일_이십삼시)).isTrue();
    }

    @Test
    void 경계_후_49시간은_마감하지_않는다() {
        ZonedDateTime 십월_삼일_일시 = ZonedDateTime.of(2026, 10, 3, 1, 0, 0, 0, 서울);

        assertThat(SeasonRolloverScheduler.isWithinRolloverWindow(십월_삼일_일시)).isFalse();
    }

    @Test
    void 달_중간에_배포해도_지난달을_소급해서_닫지_않는다() {
        // 이게 없으면 배포 직후 첫 스캔에서 전 사용자 점수가 예고 없이 리셋된다
        ZonedDateTime 구월_이십칠일 = ZonedDateTime.of(2026, 9, 27, 14, 0, 0, 0, 서울);

        assertThat(SeasonRolloverScheduler.isWithinRolloverWindow(구월_이십칠일)).isFalse();
    }

    @Test
    void 판정은_커뮤니티_타임존을_따른다() {
        // 같은 절대 시각(UTC 2026-09-30 16:00)이라도 서울은 이미 10월 1일 01시,
        // 뉴욕은 아직 9월 30일 12시다 — 서울만 마감 대상이다
        ZonedDateTime utc = ZonedDateTime.of(2026, 9, 30, 16, 0, 0, 0, ZoneId.of("UTC"));

        assertThat(SeasonRolloverScheduler.isWithinRolloverWindow(
            utc.withZoneSameInstant(서울))).isTrue();
        assertThat(SeasonRolloverScheduler.isWithinRolloverWindow(
            utc.withZoneSameInstant(ZoneId.of("America/New_York")))).isFalse();
    }
}
