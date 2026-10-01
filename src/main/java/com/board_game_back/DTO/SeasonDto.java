package com.board_game_back.DTO;

import com.board_game_back.Entity.RoomSeason;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 시즌 응답. 시즌은 방마다 따로 돈다 ({@link RoomSeason}, 기획 §22).
 * 커뮤니티는 시즌 대신 최근 N일 현황을 본다.
 */
public class SeasonDto {

    // ── 커뮤니티 현황 (최근 N일, 조회할 때마다 계산) ──

    public record StatusResponse(
        Long communityId,
        String communityName,
        String communityImageUrl,
        String inviteCode,
        LocalDate from,       // 커뮤니티 타임존 기준 집계 시작일 (포함)
        LocalDate to,         // 오늘 (포함)
        int windowDays,
        int totalRooms,       // 커뮤니티의 현재 방 수
        long totalMembers,    // 커뮤니티의 현재 인원
        int matchCount,       // 기간 안 경기 수
        List<Leader> leaders, // 기간 안 점수 상승 합 상위 3명
        List<Award> awards
    ) {}

    /** 현황 상위권 한 칸. 동점은 같은 순위를 받는다. */
    public record Leader(
        int rank,
        Long memberId,
        String nickname,
        String profileImage,
        double climb,       // 기간 안 ratingChange 합
        int playCount,
        int winCount
    ) {}

    /** type: MOST_WINS | LONGEST_STREAK | DARK_HORSE */
    public record Award(
        String type,
        Long memberId,
        String nickname,
        String profileImage,
        double value
    ) {}

    // ── 방별 시즌 ──

    /**
     * 방 시즌 한 개. 진행 중 시즌 조회·수정 응답과 마감된 시즌 목록이 같은 모양을 쓴다.
     *
     * <p>{@code startAt}·{@code endAt}은 매치 기록의 {@code playedAt}과 같은 "UTC + Z" 문자열이라
     * 프론트가 그대로 비교한다. {@code endAt}은 배타적이다 (종료일 다음 날 00:00).
     * 날짜({@code startDate}·{@code endDate})는 커뮤니티 타임존 기준이고 화면 표시·수정 폼용이다.
     *
     * <p>{@code seasonKey}는 월간 시즌 시절 스냅샷의 "2026-09"다. 구버전 앱이 월 라벨로 쓰므로 남긴다.
     */
    public record RoomSeasonResponse(
        Long seasonId,
        int seasonNumber,
        String name,          // null이면 화면이 "시즌 N"
        LocalDate startDate,
        LocalDate endDate,
        String startAt,
        String endAt,
        boolean closed,
        String seasonKey,
        int matchCount,
        int playerCount
    ) {
        public static RoomSeasonResponse of(RoomSeason season, ZoneId zone, int matchCount, int playerCount) {
            return of(season, zone, null, matchCount, playerCount);
        }

        public static RoomSeasonResponse of(
            RoomSeason season, ZoneId zone, String seasonKey, int matchCount, int playerCount) {
            return new RoomSeasonResponse(
                season.getId(), season.getSeasonNumber(), season.getName(),
                season.startDate(zone), season.endDate(zone),
                utc(season.getStartAt()), utc(season.getEndAt()),
                season.isClosed(), seasonKey, matchCount, playerCount);
        }

        private static String utc(LocalDateTime at) {
            return at.toString() + "Z";
        }
    }

    public record UpdateSeasonRequest(String name, LocalDate endDate) {}

    /**
     * 시상대 한 칸. 동점은 같은 순위를 받으므로(1,2,2) 순위가 중복될 수 있어
     * {@code first/second/third} 세 칸이 아니라 목록으로 돌려준다.
     */
    public record PodiumEntry(
        int rank,
        Long memberId,
        String nickname,
        String profileImage,
        double displayScore,
        int playCount,
        int winCount
    ) {}

    /** 시즌별 내 점수 추이 (시즌 탭 ④) */
    public record SeasonHistoryItem(
        Long seasonId,
        int seasonNumber,
        String seasonName,
        double displayScore,
        int rank,
        int playCount
    ) {}

    /** 프로필 트로피 선반 — 1~3위만, 시상 조건을 통과한 것만 */
    public record TrophyResponse(
        Long seasonId,
        int seasonNumber,
        String seasonName,
        Long roomId,
        String roomName,
        Long boardGameId,
        String boardGameName,
        int rank
    ) {}
}
