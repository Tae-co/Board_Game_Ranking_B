package com.board_game_back.Controller;

import com.board_game_back.DTO.MatchDto;
import com.board_game_back.DTO.MatchDto.ResultResponse;
import com.board_game_back.Service.MatchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MatchController {

    private final MatchService matchService;

    @PostMapping
    public ResponseEntity<List<ResultResponse>> submitMatchResult(
        @RequestBody MatchDto.ResultRequest request) {
        List<MatchDto.ResultResponse> response = matchService.recordMatchResult(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{matchId}")
    public ResponseEntity<Void> updateMatch(
        @PathVariable Long matchId,
        @RequestBody MatchDto.UpdateRequest request) {
        matchService.updateMatch(matchId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{matchId}")
    public ResponseEntity<Void> deleteMatch(
        @PathVariable Long matchId,
        @RequestParam Long requesterId) {
        matchService.deleteMatch(matchId, requesterId);
        return ResponseEntity.ok().build();
    }
}
