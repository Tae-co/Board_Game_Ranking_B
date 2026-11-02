package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreMatchParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StoreMatchParticipantRepository extends JpaRepository<StoreMatchParticipant, Long> {

    List<StoreMatchParticipant> findByStoreMatchIdOrderByIdAsc(Long storeMatchId);

    List<StoreMatchParticipant> findByStoreMatchIdIn(Collection<Long> storeMatchIds);

    /** 매장 MVP: [memberId, 참여 판 수, 방문 일수(영업일)] — 기록된 판만 */
    @Query("""
        SELECT p.memberId, COUNT(p), COUNT(DISTINCT m.businessDate), MAX(m.endedAt)
        FROM StoreMatchParticipant p JOIN StoreMatch m ON m.id = p.storeMatchId
        WHERE m.storeId = :storeId AND m.status = com.board_game_back.Entity.StoreMatch.Status.RECORDED
          AND p.memberId IS NOT NULL
        GROUP BY p.memberId
        """)
    List<Object[]> activityByStore(@Param("storeId") Long storeId);

    /** 게임방별 참여 인원 수: [storeRoomId, 사람 수] */
    @Query("""
        SELECT m.storeRoomId, COUNT(DISTINCT p.memberId)
        FROM StoreMatchParticipant p JOIN StoreMatch m ON m.id = p.storeMatchId
        WHERE m.storeId = :storeId AND m.status = com.board_game_back.Entity.StoreMatch.Status.RECORDED
          AND p.memberId IS NOT NULL
        GROUP BY m.storeRoomId
        """)
    List<Object[]> playerCountByRoom(@Param("storeId") Long storeId);

    @Modifying
    @Query("UPDATE StoreMatchParticipant p SET p.memberId = NULL WHERE p.memberId = :memberId")
    void anonymizeMember(@Param("memberId") Long memberId);

    /** 매장 나가기: 그 매장 판의 내 줄만 '나간 멤버'로 */
    @Modifying
    @Query("""
        UPDATE StoreMatchParticipant p SET p.memberId = NULL
        WHERE p.memberId = :memberId
          AND p.storeMatchId IN (SELECT m.id FROM StoreMatch m WHERE m.storeId = :storeId)
        """)
    void anonymizeMemberInStore(@Param("storeId") Long storeId, @Param("memberId") Long memberId);
}
