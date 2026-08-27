package com.board_game_back.Controller;

import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Service.SeasonService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/communities/{communityId}/seasons")
@RequiredArgsConstructor
public class SeasonController {

    private final SeasonService seasonService;

    @GetMapping
    public ResponseEntity<List<SeasonDto.PeriodResponse>> getPeriods(@PathVariable Long communityId) {
        return ResponseEntity.ok(seasonService.getPeriods(communityId));
    }

    /** period 형식: yyyy-MM (예: 2026-08) */
    @GetMapping("/{period}")
    public ResponseEntity<SeasonDto.SummaryResponse> getSummary(
            @PathVariable Long communityId,
            @PathVariable String period) {
        return ResponseEntity.ok(seasonService.getSummary(communityId, period));
    }
}
