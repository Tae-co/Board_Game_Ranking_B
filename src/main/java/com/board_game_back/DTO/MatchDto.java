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
        String scoresJson
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
