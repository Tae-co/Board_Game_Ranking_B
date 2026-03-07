package com.board_game_back.Repository;

import com.board_game_back.Entity.RoomMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    List<RoomMember> findByMemberId(Long memberId);
    List<RoomMember> findByRoomId(Long roomId);

    Optional<RoomMember> findByRoomIdAndMemberId(Long roomId, Long memberId);
    void deleteByRoomIdAndMemberId(Long roomId, Long memberId);
}
