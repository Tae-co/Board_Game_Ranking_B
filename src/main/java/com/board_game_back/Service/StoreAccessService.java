package com.board_game_back.Service;

import com.board_game_back.Entity.StoreStaff;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.StoreMemberRepository;
import com.board_game_back.Repository.StoreStaffRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 매장 권한 판정. 요청마다 DB를 읽는다 — JWT의 role만 믿으면 역할을 뺀 뒤에도 access 토큰 30분 동안
 * STAFF 동작이 된다 (plan-store §6.6 보안 규칙).
 */
@Service
@RequiredArgsConstructor
public class StoreAccessService {

    public static final String STAFF_ROLE = "STAFF";

    public enum ViewerRole { STAFF, STAFF_OTHER, MEMBER, VISITOR }

    private final MemberRepository memberRepository;
    private final StoreStaffRepository storeStaffRepository;
    private final StoreMemberRepository storeMemberRepository;

    public boolean isStaff(Long memberId) {
        return memberRepository.findById(memberId)
            .map(m -> STAFF_ROLE.equals(m.getRole()))
            .orElse(false);
    }

    /** STAFF 역할이 빠졌으면 지정 행이 남아 있어도 없는 것으로 본다 */
    public Optional<Long> staffStoreId(Long memberId) {
        if (!isStaff(memberId)) return Optional.empty();
        return storeStaffRepository.findByMemberId(memberId).map(StoreStaff::getStoreId);
    }

    public ViewerRole viewerRole(Long storeId, Long memberId) {
        if (isStaff(memberId)) {
            boolean assignedHere = storeStaffRepository.findByMemberId(memberId)
                .map(s -> s.getStoreId().equals(storeId))
                .orElse(false);
            return assignedHere ? ViewerRole.STAFF : ViewerRole.STAFF_OTHER;
        }
        return storeMemberRepository.findByStoreIdAndMemberId(storeId, memberId).isPresent()
            ? ViewerRole.MEMBER
            : ViewerRole.VISITOR;
    }

    public void requireStaffOf(Long storeId, Long memberId) {
        if (viewerRole(storeId, memberId) != ViewerRole.STAFF) {
            throw new StoreException(StoreErrorCode.NOT_STAFF_OF_STORE);
        }
    }
}
