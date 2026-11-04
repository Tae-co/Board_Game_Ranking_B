package com.board_game_back.Controller;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Service.ScoreSheetRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 6c 점수판 요청 관리. /api/admin/** 는 ADMIN만 */
@RestController
@RequestMapping("/api/admin/score-requests")
@RequiredArgsConstructor
public class AdminScoreRequestController {

    private final ScoreSheetRequestService scoreSheetRequestService;

    @GetMapping
    public ResponseEntity<List<StoreDto.AdminScoreRequest>> list() {
        return ResponseEntity.ok(scoreSheetRequestService.adminList());
    }

    @PutMapping("/{requestId}/status")
    public ResponseEntity<List<StoreDto.AdminScoreRequest>> setStatus(
            @PathVariable Long requestId,
            @RequestBody StoreDto.StatusRequest request) {
        return ResponseEntity.ok(scoreSheetRequestService.setStatus(requestId, request.status()));
    }
}
