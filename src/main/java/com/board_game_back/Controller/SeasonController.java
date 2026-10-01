package com.board_game_back.Controller;

import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Service.SeasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/communities/{communityId}/status")
@RequiredArgsConstructor
public class SeasonController {

    private final SeasonService seasonService;

    /** 커뮤니티 현황 — 최근 30일. 조회할 때마다 계산한다 (기획 §22). */
    @GetMapping
    public ResponseEntity<SeasonDto.StatusResponse> getStatus(@PathVariable Long communityId) {
        return ResponseEntity.ok(seasonService.getStatus(communityId));
    }
}
