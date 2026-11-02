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
                               PartyBrief party, Mvp mvp, List<GameRanking> rankings,
                               SeasonInfo season, ResetNotice resetNotice, ClosedRecord closedRecord) {}

    public record StoreInfo(Long id, String name, String branch, String address, String region, String phone,
                            String imageUrl, String openTime, String closeTime, boolean closed, String closedAt) {}

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
                                  List<RankItem> items, RankItem me, SeasonInfo season) {}

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

    public record MatchSummary(Long id, String startedAt, String endedAt, List<MatchParticipantItem> participants) {}

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
                                String confirmDeadline, String endedAt, String serverNow, Long disputedByMemberId, String disputedByNickname,
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

    // ── 매장 기기 (2l·4·4f·4j·6g·6k) ──

    public record PairCodeResponse(String code, String nonce, String expiresAt, String serverNow) {}

    /** status: WAITING / CLAIMED / EXPIRED. deviceToken은 CLAIMED 응답에 한 번만 */
    public record PairStatusResponse(String status, String deviceToken, String kind) {}

    /** kind: TV / TABLE */
    public record RegisterDeviceRequest(String code, String kind, String name) {}

    /** connected = 기기가 토큰을 받아 갔음 */
    public record DeviceItem(Long id, String kind, String name, boolean connected, String createdAt,
                             String lastSeenAt, String lastIp) {}

    public record DeviceBrief(Long id, String kind, String name) {}

    public record QrInfo(String url, String expiresAt) {}

    public record TvSetting(List<String> order, int intervalSec, boolean showJustFinished, List<Long> top10HiddenGameIds) {}

    public record Top10(Long boardGameId, String name, List<RankItem> items) {}

    public record JustFinishedPlayer(Integer placement, String nickname, Double gameScore, Integer ratingChange) {}

    public record JustFinished(Long matchId, String gameName, String startedAt, String endedAt,
                               List<JustFinishedPlayer> players) {}

    /**
     * 기기 화면. 테이블 기기는 store·device·qr만 (mvp·top10·justFinished·setting은 null).
     * qr은 매장 지역 정각에 바뀐다 — 기기는 expiresAt(서버 시각 기준)에 다시 부른다 (§6.6).
     */
    public record DisplayResponse(DeviceBrief device, StoreBrief store, String serverNow, QrInfo qr,
                                  TvSetting setting, Mvp mvp, List<Top10> top10, List<JustFinished> justFinished,
                                  SeasonInfo season, SeasonResult seasonResult) {}

    /** 6k: 설정 + TOP 10 게임 후보(열린 게임) + 미리 보기 데이터 */
    public record TvSettingResponse(TvSetting setting, List<TvGame> games, long tvCount, String tvLastSeenAt,
                                    Mvp mvp, List<Top10> top10, SeasonResult seasonResult) {}

    public record TvGame(Long boardGameId, String name, long matchCount) {}

    // ── 시즌 (3l·4b·6 시즌) — 날짜는 매장 영업일 "yyyy-MM-dd" ──

    public record SeasonInfo(Long id, String name, String startDate, String endDate, long daysLeft) {}

    /** cycle: MONTHLY / QUARTERLY / CUSTOM. next 날짜는 CUSTOM일 때만 쓰고, 시작일은 늘 이번 시즌 종료 다음 날 */
    public record SeasonForm(String currentName, String currentStart, String currentEnd, String cycle,
                             String nextName, String nextEnd) {}

    public record AdminSeasonResponse(SeasonInfo current, SeasonInfo next, String cycle, String today) {}

    public record SeasonTabResponse(SeasonInfo current, long players, MySeason mine, List<PastSeason> past) {}

    public record MySeason(int rank, int score, int games, int first, int second, int third, int fourthPlus,
                           Double avgPlacement) {}

    public record PastSeason(Long id, String name, String startDate, String endDate, long players, Integer myRank,
                             List<RankItem> top) {}

    /** 4b: 끝난 뒤 1주일. winners = 게임별 1위 */
    public record SeasonResult(String name, List<GameLeader> winners, MvpItem mostVisits, MvpItem mostGames,
                               String nextStart) {}

    // ── 신고 · 점수 초기화 (3g·6b·2 안내 카드) ──

    /** reason: NOT_PLAYED / THROWING / OTHER */
    public record ReportRequest(String reason, String memo) {}

    /** status: OPEN / RESET / UNDONE / CLOSED */
    public record AdminReportGroup(Long boardGameId, String gameName, Long targetMemberId, String targetNickname,
                                   long count, String lastAt, String status) {}

    public record AdminReportDetail(Long boardGameId, String gameName, Long targetMemberId, String targetNickname,
                                    Integer targetRank, Integer targetScore, Integer targetGames,
                                    List<ReportItem> reports, List<TargetMatch> recent, long recentWins,
                                    long recentTotal, List<ResetCandidate> candidates, String status,
                                    String resolvedAt, String batch, List<ResetResult> resets) {}

    public record ReportItem(String reason, String memo, String createdAt) {}

    public record TargetMatch(String endedAt, List<String> others, Integer placement) {}

    /** together = 대상과 이 게임을 같이 한 판 수 */
    public record ResetCandidate(Long memberId, String nickname, boolean target, long together) {}

    public record ResetResult(String nickname, int before) {}

    public record ResetRequest(List<Long> memberIds) {}

    public record ResetNotice(Long id, String gameName, String matchEndedAt, String resetAt) {}

    // ── 점수판 요청 (2g·6c) ──

    public record ScoreRequestForm(String name, String howToScore, String imageUrl, Long storeId) {}

    public record ScoreRequestItem(Long id, String name, long count, boolean voted) {}

    /** status: REVIEW / MAKING / ADDED / HOLD */
    public record MyScoreRequest(Long id, String name, String status, String createdAt) {}

    public record ScoreRequestsResponse(List<ScoreRequestItem> popular, List<MyScoreRequest> mine) {}

    public record AdminScoreRequest(Long id, String name, String status, long count, List<StoreCount> stores,
                                    String lastAt, List<ScoreNote> notes, List<String> photos) {}

    public record StoreCount(String name, long count) {}

    /** visitor = 그 매장 멤버가 아닌 사람이 남긴 요청 */
    public record ScoreNote(String text, String nickname, String storeName, boolean visitor) {}

    public record StatusRequest(String status) {}

    // ── 프로필 매장 탭 (7) · 운영 끝난 매장 (2h) · 월간 리포트 (8) ──

    public record StoreProfile(Long storeId, String name, String branch, boolean closed, String seasonName,
                               int bestScore, int wins, int losses, Integer rank, Integer rankTotal,
                               String topGameName, int topGameCount, int visits, List<ResetNotice> resets) {}

    public record ClosedRecord(Integer lastRank, int games, int visits) {}

    /** heat[요일 0=월..6=일][칸] = 그 시간에 시작한 판 수. 칸은 영업 시작 시각부터 3시간씩 4칸 (heatHours) */
    public record MonthlyReport(String month, StoreBrief store, long matches, long matchesPrev, long visitors,
                                long visitorsPrev, long newMembers, long newMembersPrev, int revisitRate,
                                List<GameCount> popular, List<Integer> heatHours, List<List<Integer>> heat,
                                List<Regular> regulars) {}

    public record GameCount(String name, long count) {}

    public record Regular(String nickname, long days, long games) {}

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
