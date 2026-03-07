package com.board_game_back.DTO;

public class RankingDto {

    public record GameRankingResponse(
        int rank,
        Long memberId,
        String nickname,
        double rating,
        int playCount,
        int winCount,    // 추가
        int loseCount    // 추가
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
