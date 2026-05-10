package com.board_game_back.Controller;

import com.board_game_back.DTO.RankingDto;
import com.board_game_back.Service.RankingService;
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
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @GetMapping("/game/{boardGameId}")
    public ResponseEntity<List<RankingDto.GameRankingResponse>> getGameRanking(
            @PathVariable Long boardGameId) {
        return ResponseEntity.ok(rankingService.getGameRanking(boardGameId));
    }

    @GetMapping("/room/{roomId}/game/{boardGameId}")
    public ResponseEntity<List<RankingDto.GameRankingResponse>> getRoomRanking(
            @PathVariable Long roomId,
            @PathVariable Long boardGameId) {
        return ResponseEntity.ok(rankingService.getRoomRanking(roomId, boardGameId));
    }
}
