package com.board_game_back.Controller;

import com.board_game_back.DTO.RankingDto;
import com.board_game_back.DTO.RankingDto.GameRankingResponse;
import com.board_game_back.DTO.RoomDto;
import com.board_game_back.Entity.Room;
import com.board_game_back.Service.RankingService;
import com.board_game_back.Service.RoomService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RankingController {

    private final RankingService rankingService;
    private final RoomService roomService;

    // [GET] /api/rankings/{boardGameId}
    // 특정 보드게임의 리더보드 조회 API
    @GetMapping("/{boardGameId}")
    public ResponseEntity<List<GameRankingResponse>> getGameRanking(
        @PathVariable Long boardGameId) {

        // 2. null 대신 진짜 서비스 호출!
        List<RankingDto.GameRankingResponse> response = rankingService.getGameRanking(boardGameId);
        return ResponseEntity.ok(response);
    }

    /**
     * 5. 특정 방의 상세 정보 조회 (Invite.jsx 상단 정보용)
     * GET /api/rooms/{roomId}
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<RoomDto.Response> getRoomDetail(@PathVariable Long roomId) {
        Room room = roomService.getRoomById(roomId); // 서비스에 이 메서드가 있다고 가정!
        return ResponseEntity.ok(new RoomDto.Response(room.getId(), room.getName(), room.getInviteCode()));
    }

}
