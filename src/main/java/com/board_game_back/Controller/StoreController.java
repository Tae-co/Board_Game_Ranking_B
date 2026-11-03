package com.board_game_back.Controller;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 손님 쪽 매장 API. scan만 로그인 없이 연다 (SecurityConfig). */
@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    public ResponseEntity<List<StoreDto.DirectoryItem>> getStores(
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(storeService.directory(q, memberId));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<StoreDto.MyStoreItem>> getMyStores(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(storeService.mine(memberId));
    }

    /** QR을 찍은 순간 확인. 로그인 전에도 부른다 */
    @PostMapping("/{storeId}/scan")
    public ResponseEntity<StoreDto.ScanResponse> scan(
            @PathVariable Long storeId,
            @RequestBody StoreDto.ScanRequest request) {
        return ResponseEntity.ok(storeService.scan(storeId, request.token()));
    }

    @GetMapping("/{storeId}/home")
    public ResponseEntity<StoreDto.HomeResponse> getHome(
            @PathVariable Long storeId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(storeService.home(storeId, memberId));
    }

    @GetMapping("/{storeId}/events")
    public ResponseEntity<StoreDto.EventsResponse> getEvents(
            @PathVariable Long storeId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(storeService.events(storeId, memberId));
    }

    @PostMapping("/{storeId}/join")
    public ResponseEntity<StoreDto.CheckinResponse> join(
            @PathVariable Long storeId,
            @RequestBody StoreDto.JoinRequest request,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(storeService.join(storeId, memberId, request));
    }

    @PostMapping("/{storeId}/checkin")
    public ResponseEntity<StoreDto.CheckinResponse> checkIn(
            @PathVariable Long storeId,
            @RequestBody StoreDto.CheckinRequest request,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(storeService.checkIn(storeId, memberId, request.ticket()));
    }

    @DeleteMapping("/{storeId}/membership")
    public ResponseEntity<Void> leave(
            @PathVariable Long storeId,
            @AuthenticationPrincipal Long memberId) {
        storeService.leave(storeId, memberId);
        return ResponseEntity.noContent().build();
    }

    /** STAFF 폰 체크인 QR (2m) */
    @GetMapping("/{storeId}/checkin-qr")
    public ResponseEntity<StoreDto.CheckinQrResponse> getCheckinQr(
            @PathVariable Long storeId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(storeService.checkinQr(storeId, memberId));
    }
}
