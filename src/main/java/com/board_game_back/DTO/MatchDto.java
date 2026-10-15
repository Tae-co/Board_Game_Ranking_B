package com.board_game_back.DTO;

import java.util.List;

public class MatchDto {

    public record ResultRequest(
        Long boardGameId,
        Long roomId,
        List<ParticipantRequest> participants
    ) {}

    public record ParticipantRequest(
        Long memberId,
        int placement,
        String scoresJson,
        Integer chipDelta, // 칩 게임에서만. 이번 판 손익
        Integer buyIns     // 칩 게임에서만. 이번 판 바이인 횟수 (첫 바이인 포함, 1 이상)
    ) {}

    public record ResultResponse(
        Long memberId,
        String nickname,
        int placement,
        double ratingChange
    ) {}

    public record MatchHistoryResponse(
        Long matchId,
        Long boardGameId,
        String gameName,
        String playedAt,
        List<ParticipantHistoryResponse> participants
    ) {}

    public record ParticipantHistoryResponse(
        Long memberId,
        String nickname,
        String profileImage,
        int placement,
        double ratingChange,
        String scoresJson
    ) {}
}
