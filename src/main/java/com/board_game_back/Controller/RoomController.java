package com.board_game_back.Controller;

import com.board_game_back.DTO.MatchDto;
import com.board_game_back.DTO.RankingDto;
import com.board_game_back.DTO.RoomDto;
import com.board_game_back.DTO.RoomDto.Response;
import com.board_game_back.Entity.Room;
import com.board_game_back.Service.MatchService;
import com.board_game_back.Service.RankingService;
import com.board_game_back.Service.RoomService;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RoomController {

    private final RoomService roomService;
    private final RankingService rankingService;
    private final MatchService matchService;

    @PostMapping
    public ResponseEntity<RoomDto.Response> createRoom(
            @RequestBody RoomDto.CreateRequest request,
            @AuthenticationPrincipal Long memberId) {
        Room room = roomService.createRoom(request.roomName(), memberId, request.boardGameId(), request.communityId());
        return ResponseEntity.ok(
            new RoomDto.Response(room.getId(), room.getName(), room.getInviteCode(), room.getBoardGameId()));
    }

    @PostMapping("/join")
    public ResponseEntity<RoomDto.Response> joinRoom(
            @RequestBody RoomDto.JoinRequest request,
            @AuthenticationPrincipal Long memberId) {
        Room room = roomService.joinRoom(request.inviteCode(), memberId);
        return ResponseEntity.ok(
            new RoomDto.Response(room.getId(), room.getName(), room.getInviteCode(), room.getBoardGameId()));
    }

    @GetMapping("/my/{memberId}")
    public ResponseEntity<List<Response>> getMyRooms(@PathVariable Long memberId) {
        List<RoomDto.Response> responses = roomService.getMyRooms(memberId).stream()
            .map(r -> new RoomDto.Response(r.getId(), r.getName(), r.getInviteCode(), r.getBoardGameId()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{roomId}/members")
    public ResponseEntity<List<RoomDto.RoomMemberResponse>> getRoomMembers(@PathVariable Long roomId) {
        return ResponseEntity.ok(roomService.getRoomMembers(roomId));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomDto.Response> getRoomDetail(@PathVariable Long roomId) {
        Room room = roomService.getRoomById(roomId);
        return ResponseEntity.ok(
            new RoomDto.Response(room.getId(), room.getName(), room.getInviteCode(), room.getBoardGameId()));
    }

    @GetMapping("/{roomId}/rankings")
    public ResponseEntity<List<RankingDto.GameRankingResponse>> getRoomRankings(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long boardGameId) {
        Long gameId = boardGameId != null ? boardGameId : roomService.getRoomById(roomId).getBoardGameId();
        if (gameId == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(rankingService.getRoomRanking(roomId, gameId));
    }

    @DeleteMapping("/{roomId}/members/{memberId}")
    public ResponseEntity<String> leaveRoom(
            @PathVariable Long roomId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal Long requesterId) {
        roomService.leaveRoom(roomId, memberId, requesterId);
        return ResponseEntity.ok("방을 나갔습니다.");
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<String> deleteRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal Long requesterId) {
        roomService.deleteRoom(roomId, requesterId);
        return ResponseEntity.ok("방이 삭제되었습니다.");
    }

    @GetMapping("/{roomId}/matches")
    public ResponseEntity<List<MatchDto.MatchHistoryResponse>> getMatchHistory(@PathVariable Long roomId) {
        return ResponseEntity.ok(matchService.getMatchHistory(roomId));
    }

    @PatchMapping("/{roomId}/name")
    public ResponseEntity<String> updateRoomName(
            @PathVariable Long roomId,
            @RequestBody java.util.Map<String, Object> body,
            @AuthenticationPrincipal Long requesterId) {
        Object roomNameObj = body.get("roomName");
        if (roomNameObj == null) return ResponseEntity.badRequest().body("방 이름을 입력해주세요.");
        String newName = roomNameObj.toString().trim();
        if (newName.isBlank()) return ResponseEntity.badRequest().body("방 이름을 입력해주세요.");
        roomService.updateRoomName(roomId, requesterId, newName);
        return ResponseEntity.ok("방 이름이 변경되었습니다.");
    }

    @PutMapping("/{roomId}/members/{memberId}/rating")
    public ResponseEntity<String> updateMemberRating(
            @PathVariable Long roomId,
            @PathVariable Long memberId,
            @RequestBody RoomDto.UpdateRatingRequest request,
            @AuthenticationPrincipal Long requesterId) {
        roomService.updateMemberRating(roomId, memberId, requesterId, request.rating());
        return ResponseEntity.ok("점수가 업데이트되었습니다.");
    }
}
