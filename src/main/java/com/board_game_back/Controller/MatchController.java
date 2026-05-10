package com.board_game_back.Controller;

import com.board_game_back.DTO.MatchDto;
import com.board_game_back.DTO.MatchDto.ResultResponse;
import com.board_game_back.Service.MatchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MatchController {

    private final MatchService matchService;

    @PostMapping
    public ResponseEntity<List<ResultResponse>> submitMatchResult(
            @RequestBody MatchDto.ResultRequest request,
            @AuthenticationPrincipal Long requesterId) {
        return ResponseEntity.ok(matchService.recordMatchResult(request, requesterId));
    }

    @PutMapping("/{matchId}")
    public ResponseEntity<List<ResultResponse>> updateMatch(
            @PathVariable Long matchId,
            @RequestBody MatchDto.ResultRequest request,
            @AuthenticationPrincipal Long requesterId) {
        return ResponseEntity.ok(matchService.updateMatchResult(matchId, request, requesterId));
    }

    @DeleteMapping("/{matchId}")
    public ResponseEntity<Void> deleteMatch(
            @PathVariable Long matchId,
            @AuthenticationPrincipal Long requesterId) {
        matchService.deleteMatch(matchId, requesterId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/admin/recalculate-all")
    public ResponseEntity<String> recalculateAll() {
        try {
            matchService.recalculateAllRatings();
            return ResponseEntity.ok("재계산 완료");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("재계산 실패: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
}
