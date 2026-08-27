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
}
