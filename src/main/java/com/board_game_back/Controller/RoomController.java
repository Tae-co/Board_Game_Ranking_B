package com.board_game_back.Controller;

import com.board_game_back.DTO.MatchDto;
import com.board_game_back.DTO.RankingDto;
import com.board_game_back.DTO.RoomDto;
import com.board_game_back.DTO.RoomDto.Response;
import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Entity.Room;
import com.board_game_back.Service.MatchService;
import com.board_game_back.Service.RankingService;
import com.board_game_back.Service.RoomSeasonService;
import com.board_game_back.Service.SeasonArchiveService;
import com.board_game_back.Service.RoomService;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class RoomController {

    private final RoomService roomService;
    private final RankingService rankingService;
    private final MatchService matchService;
    private final SeasonArchiveService seasonArchiveService;
    private final RoomSeasonService roomSeasonService;

    @PostMapping
    public ResponseEntity<RoomDto.Response> createRoom(
            @RequestBody RoomDto.CreateRequest request,
            @AuthenticationPrincipal Long memberId) {
        Room room = roomService.createRoom(request.roomName(), memberId, request.boardGameId(), request.communityId(),
            request.seasonName(), request.seasonEndDate());
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

    /** 초대 코드로 방만 찾는다. 참가는 방 화면의 참가하기 버튼(/join)에서 따로 한다. */
    @GetMapping("/code/{inviteCode}")
    public ResponseEntity<RoomDto.Response> getRoomByInviteCode(@PathVariable String inviteCode) {
        Room room = roomService.getRoomByInviteCode(inviteCode);
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

    /**
     * seasonId를 주면 마감된 그 시즌의 스냅샷을, 안 주면 지금까지처럼 현재 랭킹을 돌려준다.
     * 응답 스키마가 같아서 프론트 테이블은 그대로 쓴다 (기획 §8).
     */
    @GetMapping("/{roomId}/rankings")
    public ResponseEntity<List<RankingDto.GameRankingResponse>> getRoomRankings(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long boardGameId,
            @RequestParam(required = false) String seasonId,
            @RequestParam(required = false) String season) {
        // season(yyyy-MM)은 구버전 앱(1.10.x)이 보낸다. 새 앱은 seasonId를 쓴다.
        String seasonRef = seasonId != null ? seasonId : season;
        if (seasonRef != null) {
            return ResponseEntity.ok(seasonArchiveService.getSeasonRanking(roomId, boardGameId, seasonRef));
        }
        Long gameId = boardGameId != null ? boardGameId : roomService.getRoomById(roomId).getBoardGameId();
        if (gameId == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(rankingService.getRoomRanking(roomId, gameId));
    }

    /** 진행 중 시즌. 종료 시각이 지났으면 넘긴 뒤의 시즌을 준다. */
    @GetMapping("/{roomId}/seasons/current")
    public ResponseEntity<SeasonDto.RoomSeasonResponse> getCurrentSeason(@PathVariable Long roomId) {
        return ResponseEntity.ok(roomSeasonService.getCurrentSeasonResponse(roomId));
    }

    /** 호스트 전용 — 진행 중 시즌의 이름·종료일(내일 이후) 수정 */
    @PutMapping("/{roomId}/seasons/current")
    public ResponseEntity<SeasonDto.RoomSeasonResponse> updateCurrentSeason(
            @PathVariable Long roomId,
            @RequestBody SeasonDto.UpdateSeasonRequest request,
            @AuthenticationPrincipal Long requesterId) {
        return ResponseEntity.ok(
            roomSeasonService.updateCurrentSeason(roomId, requesterId, request.name(), request.endDate()));
    }

    /** 마감된 시즌 목록 (시즌 탭의 시즌 선택). 진행 중인 시즌은 들어가지 않는다. */
    @GetMapping("/{roomId}/seasons")
    public ResponseEntity<List<SeasonDto.RoomSeasonResponse>> getRoomSeasons(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long boardGameId) {
        return ResponseEntity.ok(seasonArchiveService.getRoomSeasons(roomId, boardGameId));
    }

    /** 1·2·3등 시상대. 시상 조건(참가자 3명 이상·본인 3경기 이상)을 통과한 행만 나온다. */
    /** seasonRef: 시즌 id. 구버전 앱(1.10.x)은 "yyyy-MM"을 보낸다. */
    @GetMapping("/{roomId}/seasons/{seasonRef}/podium")
    public ResponseEntity<List<SeasonDto.PodiumEntry>> getSeasonPodium(
            @PathVariable Long roomId,
            @PathVariable String seasonRef,
            @RequestParam(required = false) Long boardGameId) {
        return ResponseEntity.ok(seasonArchiveService.getPodium(roomId, boardGameId, seasonRef));
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
