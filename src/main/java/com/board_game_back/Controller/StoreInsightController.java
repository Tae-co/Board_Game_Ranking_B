package com.board_game_back.Controller;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Service.ScoreSheetRequestService;
import com.board_game_back.Service.StoreInsightService;
import com.board_game_back.Service.StoreReportService;
import com.board_game_back.Service.StoreSeasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 매장 4단계 손님·STAFF API: 시즌 탭(3l)·경기 신고(3g)·초기화 안내(2)·프로필 매장 탭(7)·월간 리포트(8)·점수판 요청(2g) */
@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreInsightController {

    private final StoreSeasonService seasonService;
    private final StoreReportService reportService;
    private final StoreInsightService insightService;
    private final ScoreSheetRequestService scoreSheetRequestService;

    @GetMapping("/{storeId}/rooms/{roomId}/seasons")
    public ResponseEntity<StoreDto.SeasonTabResponse> getSeasons(
            @PathVariable Long storeId,
            @PathVariable Long roomId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(seasonService.seasonTab(storeId, roomId, memberId));
    }

    @PostMapping("/matches/{matchId}/report")
    public ResponseEntity<Void> report(
            @PathVariable Long matchId,
            @RequestBody StoreDto.ReportRequest request,
            @AuthenticationPrincipal Long memberId) {
        reportService.report(matchId, memberId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{storeId}/reset-notice/seen")
    public ResponseEntity<Void> markResetSeen(
            @PathVariable Long storeId,
            @AuthenticationPrincipal Long memberId) {
        reportService.markSeen(storeId, memberId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile")
    public ResponseEntity<List<StoreDto.StoreProfile>> getProfile(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(insightService.profile(memberId));
    }

    @GetMapping("/{storeId}/report")
    public ResponseEntity<StoreDto.MonthlyReport> getReport(
            @PathVariable Long storeId,
            @RequestParam(required = false) String month,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(insightService.staffReport(storeId, memberId, month));
    }

    @GetMapping("/score-requests")
    public ResponseEntity<StoreDto.ScoreRequestsResponse> getScoreRequests(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(scoreSheetRequestService.view(memberId));
    }

    @PostMapping("/score-requests")
    public ResponseEntity<StoreDto.ScoreRequestsResponse> requestScoreSheet(
            @RequestBody StoreDto.ScoreRequestForm form,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(scoreSheetRequestService.request(memberId, form));
    }

    @PostMapping("/score-requests/{requestId}/vote")
    public ResponseEntity<StoreDto.ScoreRequestsResponse> voteScoreSheet(
            @PathVariable Long requestId,
            @RequestParam(required = false) Long storeId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(scoreSheetRequestService.toggleVote(memberId, requestId, storeId));
    }
}
