package com.board_game_back.DTO;

public class RoomDto {

    // 방 생성 요청
    public record CreateRequest(String roomName, Long memberId) {

    }

    // 방 가입 요청
    public record JoinRequest(String inviteCode, Long memberId) {

    }

    // 방 정보 응답
    public record Response(Long roomId, String roomName, String inviteCode) {

    }
}
