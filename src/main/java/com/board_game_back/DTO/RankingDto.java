package com.board_game_back.DTO;

public class RankingDto {

    public record GameRankingResponse(
        Integer rank,
        Long memberId,
        String nickname,
        double rating,
        int playCount,
        int winCount,
        int loseCount
    ) {

    }

    // 전체 랭킹 응답 (점수만)
    public record GlobalRankingResponse(
        int rank,
        Long memberId,
        String nickname,
        double rating
    ) {}
}
