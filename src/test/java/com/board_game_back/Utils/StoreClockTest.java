package com.board_game_back.Utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.board_game_back.Entity.Store;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** 매장 시간 — 입력·출력은 UTC 벽시계. 한국은 UTC+9 */
class StoreClockTest {

    static Store store(String region, String open, String close) {
        return Store.builder().name("야다").address("서울").region(region)
            .openTime(LocalTime.parse(open)).closeTime(LocalTime.parse(close)).qrSecret("s").build();
    }

    /** 한국 시각 → UTC */
    static LocalDateTime kst(int month, int day, int hour, int minute) {
        return LocalDateTime.of(2026, month, day, hour, minute).minusHours(9);
    }

    @Test
    void 자정을_넘기는_매장의_새벽은_전날_영업일이다() {
        Store s = store("South Korea", "14:00", "02:00");

        assertThat(StoreClock.businessDate(s, kst(11, 3, 1, 0))).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(StoreClock.businessDate(s, kst(11, 3, 14, 0))).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(StoreClock.isOpen(s, kst(11, 3, 1, 59))).isTrue();
        assertThat(StoreClock.isOpen(s, kst(11, 3, 2, 0))).isFalse();
        assertThat(StoreClock.isOpen(s, kst(11, 3, 13, 59))).isFalse();
        assertThat(StoreClock.isOpen(s, kst(11, 3, 14, 0))).isTrue();
    }

    @Test
    void 자정을_넘기는_매장의_마감은_다음날_새벽이다() {
        Store s = store("South Korea", "14:00", "02:00");

        assertThat(StoreClock.closingAt(s, kst(11, 2, 21, 0))).isEqualTo(kst(11, 3, 2, 0));
        assertThat(StoreClock.closingAt(s, kst(11, 3, 1, 0))).isEqualTo(kst(11, 3, 2, 0));
    }

    @Test
    void 자정에_닫는_매장() {
        Store s = store("South Korea", "12:00", "00:00");

        assertThat(StoreClock.isOpen(s, kst(11, 2, 23, 59))).isTrue();
        assertThat(StoreClock.isOpen(s, kst(11, 3, 0, 0))).isFalse();
        assertThat(StoreClock.closingAt(s, kst(11, 2, 15, 0))).isEqualTo(kst(11, 3, 0, 0));
    }

    @Test
    void 같은_날_닫는_매장() {
        Store s = store("South Korea", "10:00", "22:00");

        assertThat(StoreClock.isOpen(s, kst(11, 2, 21, 59))).isTrue();
        assertThat(StoreClock.isOpen(s, kst(11, 2, 22, 0))).isFalse();
        assertThat(StoreClock.closingAt(s, kst(11, 2, 12, 0))).isEqualTo(kst(11, 2, 22, 0));
    }

    @Test
    void 정각_칸은_매장_지역_시간의_정각이다() {
        Store korea = store("South Korea", "10:00", "22:00");
        Store india = store("India", "10:00", "22:00");
        LocalDateTime utc = LocalDateTime.of(2026, 11, 2, 9, 45);

        assertThat(StoreClock.hourSlotStart(korea, utc)).isEqualTo(LocalDateTime.of(2026, 11, 2, 9, 0));
        // 인도 UTC+5:30 → 15:15 현지, 정각 칸은 15:00 현지 = 09:30 UTC
        assertThat(StoreClock.hourSlotStart(india, utc)).isEqualTo(LocalDateTime.of(2026, 11, 2, 9, 30));
    }
}
