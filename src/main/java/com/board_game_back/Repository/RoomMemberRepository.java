package com.board_game_back.Repository;

import com.board_game_back.Entity.RoomMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    @Query("SELECT rm FROM RoomMember rm JOIN FETCH rm.room WHERE rm.member.id = :memberId")
    List<RoomMember> findByMemberId(@Param("memberId") Long memberId);

    @Query("SELECT rm FROM RoomMember rm JOIN FETCH rm.member WHERE rm.room.id = :roomId")
    List<RoomMember> findByRoomId(@Param("roomId") Long roomId);

    long countByRoomId(Long roomId);

    Optional<RoomMember> findByRoomIdAndMemberId(Long roomId, Long memberId);

    void deleteByRoomIdAndMemberId(Long roomId, Long memberId);

    void deleteByMember_Id(Long memberId);

    @Query("""
        SELECT rm.room.id, COUNT(rm)
        FROM RoomMember rm
        WHERE rm.room.id IN :roomIds
        GROUP BY rm.room.id
        """)
    List<Object[]> countByRoomIds(@Param("roomIds") Collection<Long> roomIds);

    @Query("""
        SELECT rm.room.id
        FROM RoomMember rm
        WHERE rm.member.id = :memberId
          AND rm.room.id IN :roomIds
        """)
    List<Long> findJoinedRoomIds(@Param("memberId") Long memberId, @Param("roomIds") Collection<Long> roomIds);
}
