package com.board_game_back.Entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RoomSeasonTest {

    private static final ZoneId 서울 = ZoneId.of("Asia/Seoul");
    private static final ZoneId 뉴욕 = ZoneId.of("America/New_York");

    @Test
    void 종료_시각은_종료일_다음_날_00시를_UTC로_옮긴_값이다() {
        // 한국 10/28 종료 → 10/29 00:00 KST = 10/28 15:00 UTC. 종료일 당일 경기까지 그 시즌에 들어간다.
        assertThat(RoomSeason.endExclusiveUtc(LocalDate.of(2026, 10, 28), 서울))
            .isEqualTo(LocalDateTime.of(2026, 10, 28, 15, 0));
        assertThat(RoomSeason.endExclusiveUtc(LocalDate.of(2026, 10, 28), 뉴욕))
            .isEqualTo(LocalDateTime.of(2026, 10, 29, 4, 0)); // EDT(UTC-4)
    }

    @Test
    void 종료일은_저장된_시각에서_그대로_돌아온다() {
        RoomSeason season = RoomSeason.open(1L, 1, "가을", LocalDateTime.of(2026, 9, 30, 15, 0),
            LocalDate.of(2026, 10, 28), 서울);

        assertThat(season.startDate(서울)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(season.endDate(서울)).isEqualTo(LocalDate.of(2026, 10, 28));
    }

    @Test
    void 다음_시즌은_같은_일수로_빈틈없이_이어진다() {
        RoomSeason season = RoomSeason.open(1L, 1, "가을", LocalDateTime.of(2026, 9, 30, 15, 0),
            LocalDate.of(2026, 10, 28), 서울);

        RoomSeason next = season.next(서울, LocalDateTime.of(2026, 10, 28, 15, 0));

        assertThat(next.getSeasonNumber()).isEqualTo(2);
        assertThat(next.getName()).isNull();
        assertThat(next.getStartAt()).isEqualTo(season.getEndAt());
        assertThat(next.startDate(서울)).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(next.endDate(서울)).isEqualTo(LocalDate.of(2026, 11, 25)); // 28일
    }

    @Test
    void 서버가_오래_죽어있었으면_빈_시즌을_여러_개_만들지_않고_종료일만_민다() {
        RoomSeason season = RoomSeason.open(1L, 1, "짧은", LocalDateTime.of(2026, 9, 30, 15, 0),
            LocalDate.of(2026, 10, 7), 서울); // 10/1~10/7, 7일짜리

        // 3주 뒤에야 롤오버가 돌았다
        RoomSeason next = season.next(서울, LocalDateTime.of(2026, 10, 27, 0, 0));

        assertThat(next.getSeasonNumber()).isEqualTo(2);
        // 10/14 · 10/21은 이미 지났다 → 10/28까지 민다
        assertThat(next.endDate(서울)).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(next.isDue(LocalDateTime.of(2026, 10, 27, 0, 0))).isFalse();
    }

    @Test
    void 이름은_앞뒤_공백을_지우고_빈_값은_null이다() {
        RoomSeason season = RoomSeason.open(1L, 1, "  봄 리그  ", LocalDateTime.now(), LocalDate.now().plusDays(3), 서울);
        assertThat(season.getName()).isEqualTo("봄 리그");

        season.rename("   ");
        assertThat(season.getName()).isNull();
    }

    @Test
    void 이름이_너무_길면_거부한다() {
        RoomSeason season = RoomSeason.open(1L, 1, "봄", LocalDateTime.now(), LocalDate.now().plusDays(3), 서울);

        assertThatThrownBy(() -> season.rename("가".repeat(RoomSeason.MAX_NAME_LENGTH + 1)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
