package com.board_game_back.DTO;

import java.util.List;

public class RankingDto {

    public record GameRankingResponse(
        Integer rank,
        Long memberId,
        String nickname,
        String profileImage,
        double rating,
        int playCount,
        int winCount,
        int loseCount,
        List<Integer> placementCounts, // 인덱스 0 = 1등. 방 랭킹에서만 채운다 (전체 랭킹은 null)
        Integer rebuyCount, // 칩 게임만. rating = 칩 잔고
        Double netProfit    // 칩 게임만. 잔고 - 받은 칩 전부. 이 순서로 정렬된다
    ) {
        public GameRankingResponse(Integer rank, Long memberId, String nickname, String profileImage, double rating,
                                   int playCount, int winCount, int loseCount, List<Integer> placementCounts) {
            this(rank, memberId, nickname, profileImage, rating, playCount, winCount, loseCount, placementCounts,
                null, null);
        }
    }
}
