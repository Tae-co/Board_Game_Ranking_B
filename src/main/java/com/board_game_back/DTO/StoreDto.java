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

    /** party·mvp·rankings는 2단계부터. party는 내가 이 매장 그룹에 있을 때만 */
    public record HomeResponse(StoreInfo store, Viewer viewer, long memberCount, List<GameItem> games,
                               PartyBrief party, Mvp mvp, List<GameRanking> rankings) {}

    public record StoreInfo(Long id, String name, String branch, String address, String region, String phone,
                            String imageUrl, String openTime, String closeTime, boolean closed) {}

    /** role: STAFF / STAFF_OTHER / MEMBER / VISITOR. checkin은 MEMBER일 때만 */
    public record Viewer(String role, Checkin checkin) {}

    public record Checkin(boolean active, String expiresAt) {}

    /** playerCount = 이 게임방에서 기록된 판에 참여한 사람 수 */
    public record GameItem(Long storeRoomId, Long boardGameId, String name, String imageUrl,
                           int minPlayers, int maxPlayers, Integer minMinutes, Integer maxMinutes, long playerCount) {}

    // ── 랭킹 · MVP ──

    public record RankItem(int rank, Long memberId, String nickname, int score, int games, boolean me) {}

    public record GameRanking(Long storeRoomId, Long boardGameId, String name, long playerCount, List<RankItem> top) {}

    /** gameLeaders = 게임별 1위. 동점이면 가장 최근에 플레이한 사람 (plan-store §1 MVP) */
    public record Mvp(MvpItem mostVisits, MvpItem mostGames, List<GameLeader> gameLeaders) {}

    public record MvpItem(Long memberId, String nickname, long count) {}

    public record GameLeader(Long boardGameId, String name, Long memberId, String nickname, int score) {}

    /** 2b. me는 순위권 밖이어도 내 순위 */
    public record RankingResponse(Long storeRoomId, Long boardGameId, String name, long playerCount,
                                  List<RankItem> items, RankItem me) {}

    // ── 매장 게임방 (3) ──

    public record RoomResponse(StoreBrief store, String openTime, String closeTime, GameItem game, boolean closed,
                               List<RankItem> ranking, StartState start) {}

    /**
     * 시작 버튼 상태. block이 null이면 시작 가능 (기록자일 때).
     * block: NO_PARTY / NOT_RECORDER / STAFF / NOT_CHECKED_IN / ROOM_CLOSED / GAME_TIME_NOT_SET / PLAYER_COUNT /
     * PARTY_PLAYING / STORE_NOT_OPEN / TOO_CLOSE_TO_CLOSING / MEMBER_NOT_CHECKED_IN / STORE_CLOSED
     */
    public record StartState(String block, PartyBrief party, String busyGameName, String closesAt,
                             List<String> expiredNicknames) {}

    public record MatchSummary(Long id, String endedAt, List<MatchParticipantItem> participants) {}

    // ── 오늘의 그룹 (2i·3b·3c) ──

    public record PartyBrief(Long id, int size, List<String> nicknames, Long recorderMemberId, String recorderNickname) {}

    public record PartyMemberItem(Long memberId, String nickname, String joinedAt, boolean recorder) {}

    /**
     * 내 그룹. currentMatch = 열린 판(없으면 null), lastMatch = 가장 최근 판(열린 판 포함).
     * expiredNicknames = 30분 안에 체크인 만료로 빠진 사람 (2i·3 안내 줄)
     */
    public record PartyResponse(Long id, StoreBrief store, String code, Long recorderMemberId,
                                List<PartyMemberItem> members, List<String> expiredNicknames,
                                MatchBrief currentMatch, MatchBrief lastMatch) {}

    public record MatchBrief(Long id, String status, Long storeRoomId, Long boardGameId, String gameName,
                             List<Long> participantIds) {}

    public record JoinPartyRequest(String code, Boolean move) {}

    // ── 판 (3d~3n) ──

    public record MatchResponse(Long id, StoreBrief store, Long storeRoomId, Long boardGameId, String gameName,
                                String status, String endReason, Long partyId, Long recorderMemberId,
                                String recorderNickname, int minMinutes, int maxMinutes, String startedAt,
                                String saveOpensAt, String saveDeadline, String closesAt, String firstSavedAt,
                                String confirmDeadline, String endedAt, String serverNow, String disputedByNickname,
                                String draftJson, int draftVersion, List<MatchParticipantItem> participants,
                                Integer myRankBefore, Integer myRankAfter) {}

    /** ratingChange 등은 기록된 판만 */
    public record MatchParticipantItem(Long memberId, String nickname, Integer placement, Double gameScore,
                                       String scoresJson, boolean confirmed, Integer ratingBefore,
                                       Integer ratingAfter, Integer ratingChange) {}

    public record DraftRequest(String draftJson) {}

    public record SubmitParticipant(Long memberId, Integer placement, Double gameScore, String scoresJson) {}

    public record SubmitRequest(List<SubmitParticipant> participants) {}

    public record CheckinQrResponse(String url, String expiresAt, String serverNow) {}

    // ── 어드민 ──

    public record StoreForm(String name, String branch, String address, String region, String phone,
                            String imageUrl, String openTime, String closeTime) {}

    public record AdminStoreItem(StoreInfo store, long memberCount, List<StaffInfo> staff, String createdAt,
                                 String closedAt) {}

    /** 6i 게임방 관리 */
    public record AdminRoomItem(Long storeRoomId, Long boardGameId, String name, String imageUrl,
                                Integer minMinutes, Integer maxMinutes, boolean closed, long matchCount) {}

    /** assignedAt은 지정된 STAFF일 때만 (후보는 null) */
    public record StaffInfo(Long memberId, String nickname, String assignedAt) {}
}
