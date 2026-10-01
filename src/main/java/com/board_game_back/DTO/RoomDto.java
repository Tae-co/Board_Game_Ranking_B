package com.board_game_back.DTO;

import java.time.LocalDate;

public class RoomDto {

    /** seasonName·seasonEndDate가 둘 다 없으면(구버전 앱) 오늘부터 4주짜리 시즌을 연다. */
    public record CreateRequest(
        String roomName, Long boardGameId, Long communityId, String seasonName, LocalDate seasonEndDate) {}

    public record JoinRequest(String inviteCode) {}

    public record Response(Long roomId, String roomName, String inviteCode, Long boardGameId) {}

    public record UpdateRatingRequest(double rating) {}

    public record RoomMemberResponse(Long memberId, String nickname, boolean isHost, String profileImage) {}
}
