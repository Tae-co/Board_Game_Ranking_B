package com.board_game_back.Controller;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Service.StoreDeviceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 매장 기기 (plan-store §6.6). {@code /api/stores/device/**}는 로그인 없이 열고(SecurityConfig)
 * 기기 토큰({@value #DEVICE_TOKEN_HEADER})을 서비스가 DB로 확인한다. 기기 등록·목록·해제는 STAFF 로그인.
 */
@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreDeviceController {

    public static final String DEVICE_TOKEN_HEADER = "X-Device-Token";

    private final StoreDeviceService deviceService;

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Railway 프록시 뒤라 X-Forwarded-For 첫 값이 실제 IP */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    // ── 기기 (비로그인) ──

    @PostMapping("/device/pair-code")
    public ResponseEntity<StoreDto.PairCodeResponse> newPairCode(HttpServletRequest request) {
        return ResponseEntity.ok(deviceService.newPairCode(clientIp(request), now()));
    }

    @GetMapping("/device/pair-code/{nonce}")
    public ResponseEntity<StoreDto.PairStatusResponse> pairStatus(@PathVariable String nonce) {
        return ResponseEntity.ok(deviceService.pairStatus(nonce, now()));
    }

    @GetMapping("/device/display")
    public ResponseEntity<StoreDto.DisplayResponse> display(
            @RequestHeader(value = DEVICE_TOKEN_HEADER, required = false) String token,
            HttpServletRequest request) {
        return ResponseEntity.ok(deviceService.display(token, clientIp(request), now()));
    }

    // ── STAFF 폰 (2l) ──

    @GetMapping("/{storeId}/devices")
    public ResponseEntity<List<StoreDto.DeviceItem>> getDevices(
            @PathVariable Long storeId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(deviceService.devices(storeId, memberId));
    }

    @PostMapping("/{storeId}/devices")
    public ResponseEntity<List<StoreDto.DeviceItem>> registerDevice(
            @PathVariable Long storeId,
            @RequestBody StoreDto.RegisterDeviceRequest request,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(deviceService.register(storeId, memberId, request, now()));
    }

    @DeleteMapping("/{storeId}/devices/{deviceId}")
    public ResponseEntity<List<StoreDto.DeviceItem>> revokeDevice(
            @PathVariable Long storeId,
            @PathVariable Long deviceId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(deviceService.revoke(storeId, deviceId, memberId));
    }
}
