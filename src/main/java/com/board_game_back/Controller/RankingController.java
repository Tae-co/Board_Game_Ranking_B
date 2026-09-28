package com.board_game_back.Controller;

import com.board_game_back.DTO.RankingDto;
import com.board_game_back.Service.RankingService;
import com.board_game_back.Service.SeasonArchiveService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;
    private final SeasonArchiveService seasonArchiveService;

    @GetMapping("/game/{boardGameId}")
    public ResponseEntity<List<RankingDto.GameRankingResponse>> getGameRanking(
            @PathVariable Long boardGameId) {
        return ResponseEntity.ok(rankingService.getGameRanking(boardGameId));
    }

    /**
     * season(yyyy-MM)을 주면 마감된 그 시즌의 스냅샷을, 안 주면 지금까지처럼 현재 랭킹을 돌려준다.
     * 응답 스키마는 양쪽이 같다 (기획 §8).
     */
    @GetMapping("/room/{roomId}/game/{boardGameId}")
    public ResponseEntity<List<RankingDto.GameRankingResponse>> getRoomRanking(
            @PathVariable Long roomId,
            @PathVariable Long boardGameId,
            @RequestParam(required = false) String season) {
        if (season != null) {
            return ResponseEntity.ok(seasonArchiveService.getSeasonRanking(roomId, boardGameId, season));
        }
        return ResponseEntity.ok(rankingService.getRoomRanking(roomId, boardGameId));
    }
}
