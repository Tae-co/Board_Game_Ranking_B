package com.board_game_back.DTO;

public class RoomDto {

    public record CreateRequest(String roomName, Long boardGameId, Long communityId) {}

    public record JoinRequest(String inviteCode) {}

    public record Response(Long roomId, String roomName, String inviteCode, Long boardGameId) {}

    public record UpdateRatingRequest(double rating) {}

    public record RoomMemberResponse(Long memberId, String nickname, boolean isHost, String profileImage) {}
}
