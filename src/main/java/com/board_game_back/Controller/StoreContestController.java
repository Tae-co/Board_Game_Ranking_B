package com.board_game_back.Controller;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Service.StoreContestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 매장 5단계: 하루 대회 (plan-store §2 5a~5e). STAFF 동작은 서비스에서 DB로 권한을 확인한다. */
@RestController
@RequestMapping("/api/stores/{storeId}/contests/{contestId}")
@RequiredArgsConstructor
public class StoreContestController {

    private final StoreContestService contestService;

    @GetMapping
    public ResponseEntity<StoreDto.ContestResponse> getContest(
            @PathVariable Long storeId,
            @PathVariable Long contestId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(contestService.view(storeId, contestId, memberId));
    }

    @PostMapping("/entry")
    public ResponseEntity<StoreDto.ContestResponse> apply(
            @PathVariable Long storeId,
            @PathVariable Long contestId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(contestService.apply(storeId, contestId, memberId));
    }

    @DeleteMapping("/entry")
    public ResponseEntity<StoreDto.ContestResponse> withdraw(
            @PathVariable Long storeId,
            @PathVariable Long contestId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(contestService.withdraw(storeId, contestId, memberId));
    }

    // ── STAFF (5e·5c) ──

    @PutMapping("/arrivals/{targetId}")
    public ResponseEntity<StoreDto.ContestResponse> setArrived(
            @PathVariable Long storeId,
            @PathVariable Long contestId,
            @PathVariable Long targetId,
            @RequestBody StoreDto.ArrivalRequest request,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(contestService.setArrived(storeId, contestId, memberId, targetId, request.arrived()));
    }

    @PostMapping("/start")
    public ResponseEntity<StoreDto.ContestResponse> start(
            @PathVariable Long storeId,
            @PathVariable Long contestId,
            @RequestBody(required = false) StoreDto.ContestStartRequest request,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(contestService.start(storeId, contestId, memberId,
            request == null ? null : request.recorderMemberId()));
    }

    @PostMapping("/cancel")
    public ResponseEntity<StoreDto.ContestResponse> cancel(
            @PathVariable Long storeId,
            @PathVariable Long contestId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(contestService.cancel(storeId, contestId, memberId));
    }

    @PostMapping("/matches/{matchId}/approve")
    public ResponseEntity<StoreDto.ContestResponse> approve(
            @PathVariable Long storeId,
            @PathVariable Long contestId,
            @PathVariable Long matchId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(contestService.approve(storeId, contestId, matchId, memberId));
    }

    @PostMapping("/matches/{matchId}/send-back")
    public ResponseEntity<StoreDto.ContestResponse> sendBack(
            @PathVariable Long storeId,
            @PathVariable Long contestId,
            @PathVariable Long matchId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(contestService.sendBack(storeId, contestId, matchId, memberId));
    }
}
