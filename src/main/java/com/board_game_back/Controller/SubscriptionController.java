package com.board_game_back.Controller;

import com.board_game_back.DTO.SubscriptionDto;
import com.board_game_back.Service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 모임장 Pro 구독. **결제 연동 없음 — 플래그만 세우는 프로토타입이다.**
 * 경로에 mock을 남겨둔 건 실제 결제가 붙을 때 갈아끼울 자리를 표시하기 위해서다.
 */
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/me")
    public ResponseEntity<SubscriptionDto.Response> getMine(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(subscriptionService.getMine(memberId));
    }

    @PostMapping("/mock")
    public ResponseEntity<SubscriptionDto.Response> subscribe(
            @RequestBody SubscriptionDto.SubscribeRequest request,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(subscriptionService.subscribe(memberId, request.billing()));
    }

    /** 해지 예약. 남은 기간은 유지된다. */
    @DeleteMapping("/mock")
    public ResponseEntity<SubscriptionDto.Response> cancel(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(subscriptionService.cancel(memberId));
    }

    @PostMapping("/mock/resume")
    public ResponseEntity<SubscriptionDto.Response> resume(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(subscriptionService.resume(memberId));
    }
}
