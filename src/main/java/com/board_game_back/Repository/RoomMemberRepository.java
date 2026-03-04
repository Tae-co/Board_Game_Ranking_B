package com.board_game_back.Repository;

import com.board_game_back.Entity.RoomMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    List<RoomMember> findByMemberId(Long memberId); // 내가 가입한 방 목록 조회
    List<RoomMember> findByRoomId(Long roomId);     // 이 방에 있는 멤버들 조회
}
