package com.board_game_back.Service;

import com.board_game_back.Entity.Room;
import com.board_game_back.Exception.AppException;
import com.board_game_back.Exception.ErrorCode;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조회 권한. 로그인만 하면 남의 게임방·커뮤니티(초대코드 포함)가 다 보이던 것을 막는다.
 *
 * <ul>
 *   <li>게임방: 게임방 멤버, 그 게임방이 속한 커뮤니티의 멤버, 관리자.
 *       커뮤니티 멤버를 넣는 이유 — 로비에서 아직 참가하지 않은 게임방을 열고 "참가하기"를 누르는 흐름이
 *       게임방 정보(초대코드 포함)를 먼저 읽는다.</li>
 *   <li>커뮤니티: 커뮤니티 멤버, 관리자. (어드민은 모두 멤버이기도 하다)</li>
 *   <li>/my/{memberId} 류: 본인, 관리자.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessGuard {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;

    public void requireSelf(Long memberId, Long requesterId) {
        if (isAdmin() || (memberId != null && memberId.equals(requesterId))) return;
        throw new AppException(ErrorCode.SELF_ONLY);
    }

    public void requireRoomReader(Long roomId, Long requesterId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        if (isAdmin()) return;
        if (roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId).isPresent()) return;
        if (room.getCommunityId() != null
            && communityMemberRepository.existsByCommunityIdAndMemberId(room.getCommunityId(), requesterId)) return;
        throw new AppException(ErrorCode.ROOM_ACCESS_DENIED);
    }

    public void requireCommunityMember(Long communityId, Long requesterId) {
        if (!communityRepository.existsById(communityId)) {
            throw new AppException(ErrorCode.COMMUNITY_NOT_FOUND);
        }
        if (isAdmin()) return;
        if (communityMemberRepository.existsByCommunityIdAndMemberId(communityId, requesterId)) return;
        throw new AppException(ErrorCode.COMMUNITY_ACCESS_DENIED);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
