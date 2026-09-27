package com.board_game_back.Repository;

import com.board_game_back.Entity.MatchParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

    @Modifying
    @Query("DELETE FROM MatchParticipant mp WHERE mp.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);

    // ── decay 폭의 근거 (기획 §7): 승리 1회당 평균 표시점수 상승 ──
    // ratingChange는 이미 표시 점수 단위 델타다. 전 기간 누적이고 시즌으로 자르지 않는다 —
    // 시즌 안에서만 평균을 내면 리셋 직후엔 표본이 0이라 깎을 근거가 사라진다.

    /** 사람×방×게임 승리 평균. 사람마다 다른 폭으로 깎기 위한 값이다. */
    @Query("""
        SELECT mp.matchRecord.room.id, mp.matchRecord.boardGame.id, mp.member.id, AVG(mp.ratingChange)
        FROM MatchParticipant mp
        WHERE mp.placement = 1 AND mp.matchRecord.room IS NOT NULL
        GROUP BY mp.matchRecord.room.id, mp.matchRecord.boardGame.id, mp.member.id
        """)
    List<Object[]> findWinRatingChangeAvgByPlayer();

    /** 방×게임 승리 평균. 승리가 0회인 사람에게 대신 적용한다. */
    @Query("""
        SELECT mp.matchRecord.room.id, mp.matchRecord.boardGame.id, AVG(mp.ratingChange)
        FROM MatchParticipant mp
        WHERE mp.placement = 1 AND mp.matchRecord.room IS NOT NULL
        GROUP BY mp.matchRecord.room.id, mp.matchRecord.boardGame.id
        """)
    List<Object[]> findWinRatingChangeAvgByRoomGame();
}
