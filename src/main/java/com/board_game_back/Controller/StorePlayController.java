package com.board_game_back.Controller;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Service.StoreMatchService;
import com.board_game_back.Service.StorePartyService;
import com.board_game_back.Service.StoreRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 매장 2단계: 오늘의 그룹 · 매장 게임방 · 판 (plan-store §2 2i·3·3b~3n). */
@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StorePlayController {

    private final StorePartyService partyService;
    private final StoreMatchService matchService;
    private final StoreRankingService rankingService;

    // ── 오늘의 그룹 ──

    /** 내 그룹. 없으면 204 */
    @GetMapping("/parties/current")
    public ResponseEntity<StoreDto.PartyResponse> currentParty(@AuthenticationPrincipal Long memberId) {
        return partyService.current(memberId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/{storeId}/parties")
    public ResponseEntity<StoreDto.PartyResponse> createParty(
            @PathVariable Long storeId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(partyService.create(storeId, memberId));
    }

    @PostMapping("/{storeId}/parties/join")
    public ResponseEntity<StoreDto.PartyResponse> joinParty(
            @PathVariable Long storeId,
            @RequestBody StoreDto.JoinPartyRequest request,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(partyService.join(storeId, memberId, request.code(), Boolean.TRUE.equals(request.move())));
    }

    @PostMapping("/parties/leave")
    public ResponseEntity<Void> leaveParty(@AuthenticationPrincipal Long memberId) {
        partyService.leave(memberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/parties/disband")
    public ResponseEntity<Void> disbandParty(@AuthenticationPrincipal Long memberId) {
        partyService.disband(memberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/parties/members/{targetId}/kick")
    public ResponseEntity<StoreDto.PartyResponse> kick(
            @PathVariable Long targetId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(partyService.kick(memberId, targetId));
    }

    @PostMapping("/parties/recorder/{targetId}")
    public ResponseEntity<StoreDto.PartyResponse> handOver(
            @PathVariable Long targetId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(partyService.handOver(memberId, targetId));
    }

    // ── 매장 게임방 ──

    @GetMapping("/{storeId}/rooms/{roomId}")
    public ResponseEntity<StoreDto.RoomResponse> getRoom(
            @PathVariable Long storeId,
            @PathVariable Long roomId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(rankingService.room(storeId, roomId, memberId));
    }

    @GetMapping("/{storeId}/rooms/{roomId}/ranking")
    public ResponseEntity<StoreDto.RankingResponse> getRanking(
            @PathVariable Long storeId,
            @PathVariable Long roomId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(rankingService.ranking(storeId, roomId, memberId));
    }

    @GetMapping("/{storeId}/rooms/{roomId}/matches")
    public ResponseEntity<List<StoreDto.MatchSummary>> getRecentMatches(
            @PathVariable Long storeId,
            @PathVariable Long roomId) {
        return ResponseEntity.ok(rankingService.recentMatches(storeId, roomId));
    }

    @GetMapping("/{storeId}/games")
    public ResponseEntity<List<StoreDto.GameItem>> getGames(@PathVariable Long storeId) {
        return ResponseEntity.ok(rankingService.visibleGames(storeId));
    }

    @PostMapping("/{storeId}/rooms/{roomId}/matches")
    public ResponseEntity<StoreDto.MatchResponse> startMatch(
            @PathVariable Long storeId,
            @PathVariable Long roomId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(matchService.start(storeId, roomId, memberId));
    }

    // ── 판 ──

    @GetMapping("/matches/{matchId}")
    public ResponseEntity<StoreDto.MatchResponse> getMatch(
            @PathVariable Long matchId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(matchService.view(matchId, memberId));
    }

    @PutMapping("/matches/{matchId}/draft")
    public ResponseEntity<Map<String, Integer>> saveDraft(
            @PathVariable Long matchId,
            @RequestBody StoreDto.DraftRequest request,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(Map.of("draftVersion", matchService.saveDraft(matchId, memberId, request.draftJson())));
    }

    @PostMapping("/matches/{matchId}/submit")
    public ResponseEntity<StoreDto.MatchResponse> submit(
            @PathVariable Long matchId,
            @RequestBody StoreDto.SubmitRequest request,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(matchService.submit(matchId, memberId, request));
    }

    @PostMapping("/matches/{matchId}/confirm")
    public ResponseEntity<StoreDto.MatchResponse> confirm(
            @PathVariable Long matchId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(matchService.confirm(matchId, memberId));
    }

    @PostMapping("/matches/{matchId}/dispute")
    public ResponseEntity<StoreDto.MatchResponse> dispute(
            @PathVariable Long matchId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(matchService.dispute(matchId, memberId));
    }

    @PostMapping("/matches/{matchId}/reopen")
    public ResponseEntity<StoreDto.MatchResponse> reopen(
            @PathVariable Long matchId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(matchService.reopen(matchId, memberId));
    }

    @PostMapping("/matches/{matchId}/cancel")
    public ResponseEntity<StoreDto.MatchResponse> cancel(
            @PathVariable Long matchId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(matchService.cancel(matchId, memberId));
    }
}
