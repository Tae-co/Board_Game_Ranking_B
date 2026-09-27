package com.board_game_back.DTO;

import java.util.List;

/**
 * 시즌(월 단위) 결산 응답. 시즌은 별도 엔티티 없이 MatchRecord.playedAt의 연-월로 파생된다.
 */
public class SeasonDto {

    /** 결산 카드를 만들 수 있는 월 목록 (경기가 1판 이상 있는 달만) */
    public record PeriodResponse(
        String period,      // "2026-08"
        int matchCount
    ) {}

    public record SummaryResponse(
        Long communityId,
        String communityName,
        String communityImageUrl,
        String inviteCode,
        String period,
        int totalMatches,
        int totalPlayers,
        List<Award> awards,
        List<GameTop> gameTops
    ) {}

    /** type: MOST_WINS | BIGGEST_CLIMB | DARK_HORSE */
    public record Award(
        String type,
        Long memberId,
        String nickname,
        String profileImage,
        double value
    ) {}

    public record GameTop(
        Long boardGameId,
        String boardGameName,
        String boardGameImageUrl,
        Long memberId,
        String nickname,
        int wins
    ) {}

    // ── 방별 시즌 기록 (리셋 스냅샷에서 나온다. 위쪽 결산과 달리 방 단위다) ──

    /** 시즌 탭의 월 선택 목록. 마감된 시즌만 들어간다 — 진행 중인 시즌은 스냅샷이 없다. */
    public record RoomSeasonResponse(
        String seasonKey,
        int matchCount,
        int playerCount
    ) {}

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
        String seasonKey,
        double displayScore,
        int rank,
        int playCount
    ) {}

    /** 프로필 트로피 선반 — 1~3위만, 시상 조건을 통과한 것만 */
    public record TrophyResponse(
        String seasonKey,
        Long roomId,
        String roomName,
        Long boardGameId,
        String boardGameName,
        int rank
    ) {}
}
