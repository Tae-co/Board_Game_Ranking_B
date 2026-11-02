package com.board_game_back.DTO;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 매장 API. 시각은 ISO-8601 UTC 문자열, 영업시간은 매장 지역 시간 "HH:mm". */
public class StoreDto {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public static String iso(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC).toString();
    }

    public static String hhmm(LocalTime time) {
        return time == null ? null : time.format(HH_MM);
    }

    // ── QR 진입 ──

    public record ScanRequest(String token) {}

    /** result: VALID / EXPIRED_QR / STORE_CLOSED */
    public record ScanResponse(String result, String ticket, StoreBrief store) {}

    public record StoreBrief(Long id, String name, String branch) {}

    public record JoinRequest(String ticket, int consentVersion) {}

    public record CheckinRequest(String ticket) {}

    public record CheckinResponse(boolean checkedIn, String checkinExpiresAt) {}

    // ── 목록 ──

    public record DirectoryItem(Long id, String name, String branch, String region, String address,
                                String imageUrl, long memberCount, long gameCount, boolean joined) {}

    /** role: MEMBER / STAFF */
    public record MyStoreItem(Long id, String name, String branch, String imageUrl,
                              long memberCount, long gameCount, String role, boolean closed,
                              String closedAt) {}

    // ── 매장 홈 ──

    public record HomeResponse(StoreInfo store, Viewer viewer, long memberCount, List<GameItem> games) {}

    public record StoreInfo(Long id, String name, String branch, String address, String region, String phone,
                            String imageUrl, String openTime, String closeTime, boolean closed) {}

    /** role: STAFF / STAFF_OTHER / MEMBER / VISITOR. checkin은 MEMBER일 때만 */
    public record Viewer(String role, Checkin checkin) {}

    public record Checkin(boolean active, String expiresAt) {}

    public record GameItem(Long storeRoomId, Long boardGameId, String name, String imageUrl,
                           int minPlayers, int maxPlayers) {}

    public record CheckinQrResponse(String url, String expiresAt, String serverNow) {}

    // ── 어드민 ──

    public record StoreForm(String name, String branch, String address, String region, String phone,
                            String imageUrl, String openTime, String closeTime) {}

    public record AdminStoreItem(StoreInfo store, long memberCount, List<StaffInfo> staff, String createdAt,
                                 String closedAt) {}

    /** assignedAt은 지정된 STAFF일 때만 (후보는 null) */
    public record StaffInfo(Long memberId, String nickname, String assignedAt) {}
}
