package com.board_game_back.Controller;

import com.board_game_back.DTO.MatchDto;
import com.board_game_back.DTO.RankingDto;
import com.board_game_back.DTO.RoomDto;
import com.board_game_back.DTO.RoomDto.Response;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Service.MatchService;
import com.board_game_back.Service.RankingService;
import com.board_game_back.Service.RoomService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final RoomMemberRepository roomMemberRepository;
    private final MatchService matchService;

    /** 1. 방 만들기 */
    @PostMapping
    public ResponseEntity<RoomDto.Response> createRoom(@RequestBody RoomDto.CreateRequest request) {
        Room room = roomService.createRoom(request.roomName(), request.memberId(), request.boardGameId());
        return ResponseEntity.ok(
            new RoomDto.Response(room.getId(), room.getName(), room.getInviteCode(), room.getBoardGameId()));
    }

    /** 2. 초대 코드로 방 입장 */
    @PostMapping("/join")
    public ResponseEntity<RoomDto.Response> joinRoom(@RequestBody RoomDto.JoinRequest request) {
        Room room = roomService.joinRoom(request.inviteCode(), request.memberId());
        return ResponseEntity.ok(
            new RoomDto.Response(room.getId(), room.getName(), room.getInviteCode(), room.getBoardGameId()));
    }

    /** 3. 내가 참여 중인 방 목록 */
    @GetMapping("/my/{memberId}")
    public ResponseEntity<List<Response>> getMyRooms(@PathVariable Long memberId) {
        List<RoomDto.Response> responses = roomService.getMyRooms(memberId).stream()
            .map(r -> new RoomDto.Response(r.getId(), r.getName(), r.getInviteCode(), r.getBoardGameId()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /** 4. 방 멤버 목록 */
    @GetMapping("/{roomId}/members")
    public ResponseEntity<List<java.util.Map<String, Object>>> getRoomMembers(
            @PathVariable Long roomId) {
        List<java.util.Map<String, Object>> members = roomMemberRepository.findByRoomId(roomId)
            .stream()
            .map(rm -> {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("memberId", rm.getMember().getId());
                map.put("nickname", rm.getMember().getNickname());
                map.put("isHost", "HOST".equals(rm.getRole()));
                return map;
            })
            .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(members);
    }

    /** 5. 방 상세 정보 */
    @GetMapping("/{roomId}")
    public ResponseEntity<RoomDto.Response> getRoomDetail(@PathVariable Long roomId) {
        Room room = roomService.getRoomById(roomId);
        return ResponseEntity.ok(
            new RoomDto.Response(room.getId(), room.getName(), room.getInviteCode(), room.getBoardGameId()));
    }

    /** 6. 방별 랭킹 (boardGameId 미전달 시 방의 고정 게임 사용) */
    @GetMapping("/{roomId}/rankings")
    public ResponseEntity<List<RankingDto.GameRankingResponse>> getRoomRankings(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long boardGameId) {
        Long gameId = boardGameId != null ? boardGameId : roomService.getRoomById(roomId).getBoardGameId();
        if (gameId == null) {
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
        return ResponseEntity.ok(rankingService.getRoomRanking(roomId, gameId));
    }

    /** 7. 방 나가기 / 강퇴 - DELETE /api/rooms/{roomId}/members/{memberId} */
    @DeleteMapping("/{roomId}/members/{memberId}")
    public ResponseEntity<String> leaveRoom(
            @PathVariable Long roomId,
            @PathVariable Long memberId) {
        roomService.leaveRoom(roomId, memberId);
        return ResponseEntity.ok("방을 나갔습니다.");
    }

    /** 8. 방 삭제 (방장) - DELETE /api/rooms/{roomId} */
    @DeleteMapping("/{roomId}")
    public ResponseEntity<String> deleteRoom(@PathVariable Long roomId) {
        roomService.deleteRoom(roomId);
        return ResponseEntity.ok("방이 삭제되었습니다.");
    }

    /** 9. 방 매치 기록 - GET /api/rooms/{roomId}/matches */
    @GetMapping("/{roomId}/matches")
    public ResponseEntity<List<MatchDto.MatchHistoryResponse>> getMatchHistory(
            @PathVariable Long roomId) {
        return ResponseEntity.ok(matchService.getMatchHistory(roomId));
    }
}
