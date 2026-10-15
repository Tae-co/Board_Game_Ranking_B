package com.board_game_back.DTO;

import com.board_game_back.Entity.Room;
import java.time.LocalDate;

public class RoomDto {

    /** seasonName·seasonEndDate가 둘 다 없으면(구버전 앱) 오늘부터 4주짜리 시즌을 연다. */
    /** startingChips는 칩 게임 방에서만 쓴다. 없으면 1000. */
    public record CreateRequest(
        String roomName, Long boardGameId, Long communityId, String seasonName, LocalDate seasonEndDate,
        Integer startingChips) {}

    public record JoinRequest(String inviteCode) {}

    /** pendingStartingChips: 다음 시즌부터 적용될 시작 칩. 예약이 없으면 null */
    public record Response(Long roomId, String roomName, String inviteCode, Long boardGameId,
                           int startingChips, Integer pendingStartingChips) {
        public static Response from(Room room) {
            return new Response(room.getId(), room.getName(), room.getInviteCode(), room.getBoardGameId(),
                room.getStartingChips(), room.getPendingStartingChips());
        }
    }

    public record UpdateStartingChipsRequest(int startingChips) {}

    public record UpdateRatingRequest(double rating) {}

    public record RoomMemberResponse(Long memberId, String nickname, boolean isHost, String profileImage) {}
}
