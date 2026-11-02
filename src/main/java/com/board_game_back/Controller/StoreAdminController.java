package com.board_game_back.Controller;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Service.StoreAdminService;
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
}
