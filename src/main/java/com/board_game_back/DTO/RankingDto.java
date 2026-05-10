package com.board_game_back.DTO;

public class RankingDto {

    public record GameRankingResponse(
        Integer rank,
        Long memberId,
        String nickname,
        String profileImage,
        double rating,
        int playCount,
        int winCount,
        int loseCount
    ) {}
}
