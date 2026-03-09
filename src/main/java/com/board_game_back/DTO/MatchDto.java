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
        int placement
    ) {}

    public record ResultResponse(
        Long memberId,
        String nickname,
        int placement,
        double ratingChange
    ) {}

    public record UpdateRequest(
        Long requesterId,
        List<ParticipantRequest> participants
    ) {}

    public record MatchHistoryResponse(
        Long matchId,
        String gameName,
        String playedAt,
        List<ParticipantHistoryResponse> participants
    ) {}

    public record ParticipantHistoryResponse(
        Long memberId,
        String nickname,
        int placement,
        double ratingChange
    ) {}
}
