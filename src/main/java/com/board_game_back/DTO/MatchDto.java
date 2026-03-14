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
        String scoresJson   // nullable — MatchForm 호환
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
        Long boardGameId,
        String gameName,
        String playedAt,
        List<ParticipantHistoryResponse> participants
    ) {}

    public record ParticipantHistoryResponse(
        Long memberId,
        String nickname,
        int placement,
        double ratingChange,
        String scoresJson   // nullable (구버전 데이터)
    ) {}
}
