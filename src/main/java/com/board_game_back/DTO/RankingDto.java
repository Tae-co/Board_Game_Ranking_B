package com.board_game_back.DTO;

public class RankingDto {
    // 📤 [Response] Spring -> React: 특정 게임의 랭킹 목록 조회
    public record GameRankingResponse(
        int rank,               // 현재 순위 (1, 2, 3...)
        Long memberId,
        String nickname,
        double rating,          // 현재 점수
        int playCount           // 플레이 횟수 (예: 10판 이상 한 사람만 랭킹에 올릴 때 유용)
    ) {}
}
