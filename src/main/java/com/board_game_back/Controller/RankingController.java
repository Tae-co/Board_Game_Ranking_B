package com.board_game_back.Controller;

import com.board_game_back.DTO.RankingDto;
import com.board_game_back.DTO.RankingDto.GameRankingResponse;
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
    @GetMapping("/global")
    public ResponseEntity<List<RankingDto.GlobalRankingResponse>> getGlobalRanking() {
        return ResponseEntity.ok(rankingService.getGlobalRanking());
    }

}
