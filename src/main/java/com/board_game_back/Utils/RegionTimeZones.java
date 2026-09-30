package com.board_game_back.Utils;

import java.time.ZoneId;
import java.util.Map;

/**
 * 커뮤니티 {@code region}(국가명) → 타임존.
 *
 * <p>프론트 {@code src/constants/regions.js}의 {@code REGION_TIMEZONE}과 같은 표다.
 * 시즌 경계를 자를 때 프론트 표시와 다른 타임존을 쓰면 "1일인데 지난 시즌으로 들어갔다"가
 * 되므로 두 표는 항상 같이 고쳐야 한다.
 */
public final class RegionTimeZones {

    /** 매핑에 없는 region(구 자유입력 데이터: "서울" 등)이 쓰는 기본 타임존. */
    public static final ZoneId DEFAULT = ZoneId.of("Asia/Seoul");

    private static final Map<String, ZoneId> BY_REGION = Map.ofEntries(
        Map.entry("South Korea", ZoneId.of("Asia/Seoul")),
        Map.entry("United States", ZoneId.of("America/New_York")),
        Map.entry("Japan", ZoneId.of("Asia/Tokyo")),
        Map.entry("China", ZoneId.of("Asia/Shanghai")),
        Map.entry("United Kingdom", ZoneId.of("Europe/London")),
        Map.entry("Germany", ZoneId.of("Europe/Berlin")),
        Map.entry("France", ZoneId.of("Europe/Paris")),
        Map.entry("Canada", ZoneId.of("America/Toronto")),
        Map.entry("Australia", ZoneId.of("Australia/Sydney")),
        Map.entry("Singapore", ZoneId.of("Asia/Singapore")),
        Map.entry("Hong Kong", ZoneId.of("Asia/Hong_Kong")),
        Map.entry("Taiwan", ZoneId.of("Asia/Taipei")),
        Map.entry("Thailand", ZoneId.of("Asia/Bangkok")),
        Map.entry("Vietnam", ZoneId.of("Asia/Ho_Chi_Minh")),
        Map.entry("Indonesia", ZoneId.of("Asia/Jakarta")),
        Map.entry("Philippines", ZoneId.of("Asia/Manila")),
        Map.entry("Malaysia", ZoneId.of("Asia/Kuala_Lumpur")),
        Map.entry("India", ZoneId.of("Asia/Kolkata")),
        Map.entry("Brazil", ZoneId.of("America/Sao_Paulo")),
        Map.entry("Mexico", ZoneId.of("America/Mexico_City")),
        Map.entry("Netherlands", ZoneId.of("Europe/Amsterdam")),
        Map.entry("Sweden", ZoneId.of("Europe/Stockholm")),
        Map.entry("Norway", ZoneId.of("Europe/Oslo")),
        Map.entry("Denmark", ZoneId.of("Europe/Copenhagen")),
        Map.entry("Finland", ZoneId.of("Europe/Helsinki")),
        Map.entry("Spain", ZoneId.of("Europe/Madrid")),
        Map.entry("Italy", ZoneId.of("Europe/Rome")),
        Map.entry("Poland", ZoneId.of("Europe/Warsaw")),
        Map.entry("Russia", ZoneId.of("Europe/Moscow"))
    );

    public static ZoneId of(String region) {
        if (region == null) return DEFAULT;
        return BY_REGION.getOrDefault(region, DEFAULT);
    }

    private RegionTimeZones() {}
}
