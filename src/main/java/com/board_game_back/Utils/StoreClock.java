package com.board_game_back.Utils;

import com.board_game_back.Entity.Store;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 매장 시간 계산. 입력·출력의 {@code LocalDateTime}은 전부 UTC 벽시계다 (엔티티 시각 컬럼과 같다).
 *
 * <p><b>영업일</b>은 영업 시작 시각부터 하루다 (plan-store §1 방문). 14:00–02:00 매장의 01:00은 전날 영업일이다.
 */
public final class StoreClock {

    public static ZoneId zone(Store store) {
        return RegionTimeZones.of(store.getRegion());
    }

    public static boolean crossesMidnight(Store store) {
        return !store.getCloseTime().isAfter(store.getOpenTime());
    }

    public static boolean isOpen(Store store, LocalDateTime utcNow) {
        LocalTime t = local(store, utcNow).toLocalTime();
        LocalTime open = store.getOpenTime();
        LocalTime close = store.getCloseTime();
        if (crossesMidnight(store)) return !t.isBefore(open) || t.isBefore(close);
        return !t.isBefore(open) && t.isBefore(close);
    }

    public static LocalDate businessDate(Store store, LocalDateTime utcNow) {
        ZonedDateTime local = local(store, utcNow);
        LocalDate date = local.toLocalDate();
        return local.toLocalTime().isBefore(store.getOpenTime()) ? date.minusDays(1) : date;
    }

    /** 지금이 속한 영업일의 마감 시각 */
    public static LocalDateTime closingAt(Store store, LocalDateTime utcNow) {
        LocalDate date = businessDate(store, utcNow);
        if (crossesMidnight(store)) date = date.plusDays(1);
        return toUtc(date.atTime(store.getCloseTime()).atZone(zone(store)));
    }

    /** 그 영업일이 시작하는 시각 (UTC) */
    public static LocalDateTime openingAt(Store store, LocalDate businessDate) {
        return toUtc(businessDate.atTime(store.getOpenTime()).atZone(zone(store)));
    }

    /** UTC 시각을 매장 지역 시간으로 */
    public static ZonedDateTime toLocal(Store store, LocalDateTime utc) {
        return local(store, utc);
    }

    /** 지금이 속한 정각 칸의 시작 (매장 지역 시간의 정각) */
    public static LocalDateTime hourSlotStart(Store store, LocalDateTime utcNow) {
        return toUtc(local(store, utcNow).truncatedTo(ChronoUnit.HOURS));
    }

    private static ZonedDateTime local(Store store, LocalDateTime utcNow) {
        return utcNow.atZone(ZoneOffset.UTC).withZoneSameInstant(zone(store));
    }

    private static LocalDateTime toUtc(ZonedDateTime zoned) {
        return zoned.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private StoreClock() {}
}
