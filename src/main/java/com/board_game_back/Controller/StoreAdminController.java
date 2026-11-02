package com.board_game_back.Controller;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Service.StoreAdminService;
import com.board_game_back.Service.StoreDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 어드민 매장 탭. /api/admin/** 는 SecurityConfig에서 ADMIN만 통과한다. */
@RestController
@RequestMapping("/api/admin/stores")
@RequiredArgsConstructor
public class StoreAdminController {

    private final StoreAdminService storeAdminService;
    private final StoreDeviceService storeDeviceService;

    @GetMapping
    public ResponseEntity<List<StoreDto.AdminStoreItem>> getStores() {
        return ResponseEntity.ok(storeAdminService.list());
    }

    @PostMapping
    public ResponseEntity<StoreDto.AdminStoreItem> createStore(@RequestBody StoreDto.StoreForm form) {
        return ResponseEntity.ok(storeAdminService.create(form));
    }

    @PutMapping("/{storeId}")
    public ResponseEntity<StoreDto.AdminStoreItem> updateStore(
            @PathVariable Long storeId,
            @RequestBody StoreDto.StoreForm form) {
        return ResponseEntity.ok(storeAdminService.update(storeId, form));
    }

    @PostMapping("/{storeId}/close")
    public ResponseEntity<StoreDto.AdminStoreItem> closeStore(@PathVariable Long storeId) {
        return ResponseEntity.ok(storeAdminService.close(storeId));
    }

    @GetMapping("/staff-candidates")
    public ResponseEntity<List<StoreDto.StaffInfo>> getStaffCandidates() {
        return ResponseEntity.ok(storeAdminService.staffCandidates());
    }

    @GetMapping("/{storeId}/staff")
    public ResponseEntity<List<StoreDto.StaffInfo>> getStaff(@PathVariable Long storeId) {
        return ResponseEntity.ok(storeAdminService.staff(storeId));
    }

    @PutMapping("/{storeId}/staff/{memberId}")
    public ResponseEntity<List<StoreDto.StaffInfo>> assignStaff(
            @PathVariable Long storeId,
            @PathVariable Long memberId) {
        return ResponseEntity.ok(storeAdminService.assignStaff(storeId, memberId));
    }

    @DeleteMapping("/{storeId}/staff/{memberId}")
    public ResponseEntity<List<StoreDto.StaffInfo>> unassignStaff(
            @PathVariable Long storeId,
            @PathVariable Long memberId) {
        return ResponseEntity.ok(storeAdminService.unassignStaff(storeId, memberId));
    }

    @GetMapping("/{storeId}/rooms")
    public ResponseEntity<List<StoreDto.AdminRoomItem>> getRooms(@PathVariable Long storeId) {
        return ResponseEntity.ok(storeAdminService.rooms(storeId));
    }

    @PutMapping("/{storeId}/rooms/{roomId}/closed")
    public ResponseEntity<List<StoreDto.AdminRoomItem>> setRoomClosed(
            @PathVariable Long storeId,
            @PathVariable Long roomId,
            @RequestBody java.util.Map<String, Boolean> body) {
        return ResponseEntity.ok(storeAdminService.setRoomClosed(storeId, roomId, Boolean.TRUE.equals(body.get("closed"))));
    }

    // ── 매장 기기 (6g) · TV 화면 (6k) ──

    @GetMapping("/{storeId}/devices")
    public ResponseEntity<List<StoreDto.DeviceItem>> getDevices(@PathVariable Long storeId) {
        return ResponseEntity.ok(storeDeviceService.adminDevices(storeId));
    }

    @DeleteMapping("/{storeId}/devices/{deviceId}")
    public ResponseEntity<List<StoreDto.DeviceItem>> revokeDevice(
            @PathVariable Long storeId,
            @PathVariable Long deviceId) {
        return ResponseEntity.ok(storeDeviceService.revoke(storeId, deviceId));
    }

    @PostMapping("/{storeId}/qr-secret")
    public ResponseEntity<Void> rotateQrSecret(@PathVariable Long storeId) {
        storeDeviceService.rotateQrSecret(storeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{storeId}/tv-setting")
    public ResponseEntity<StoreDto.TvSettingResponse> getTvSetting(@PathVariable Long storeId) {
        return ResponseEntity.ok(storeDeviceService.tvSetting(storeId));
    }

    @PutMapping("/{storeId}/tv-setting")
    public ResponseEntity<StoreDto.TvSettingResponse> saveTvSetting(
            @PathVariable Long storeId,
            @RequestBody StoreDto.TvSetting setting) {
        return ResponseEntity.ok(storeDeviceService.saveTvSetting(storeId, setting));
    }
}
