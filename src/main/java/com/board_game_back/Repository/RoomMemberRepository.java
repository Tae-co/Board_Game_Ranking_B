package com.board_game_back.Repository;

import com.board_game_back.Entity.RoomMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    // JOIN FETCH로 N+1 방지
    @org.springframework.data.jpa.repository.Query("SELECT rm FROM RoomMember rm JOIN FETCH rm.room WHERE rm.member.id = :memberId")
    List<RoomMember> findByMemberId(@org.springframework.data.repository.query.Param("memberId") Long memberId);

    @org.springframework.data.jpa.repository.Query("SELECT rm FROM RoomMember rm JOIN FETCH rm.member WHERE rm.room.id = :roomId")
    List<RoomMember> findByRoomId(@org.springframework.data.repository.query.Param("roomId") Long roomId);

    Optional<RoomMember> findByRoomIdAndMemberId(Long roomId, Long memberId);
    void deleteByRoomIdAndMemberId(Long roomId, Long memberId);
}
