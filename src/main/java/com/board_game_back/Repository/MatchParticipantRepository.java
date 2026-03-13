package com.board_game_back.Repository;

import com.board_game_back.Entity.MatchParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

    // 특정 유저의 최근 전적(참가 기록) 가져오기
    // MatchRecord와 페치 조인(Fetch Join)을 사용하여 N+1 문제를 방지합니다.
    @Query("SELECT mp FROM MatchParticipant mp JOIN FETCH mp.matchRecord mr JOIN FETCH mr.boardGame WHERE mp.member.id = :memberId ORDER BY mr.playedAt DESC")
    List<MatchParticipant> findRecentMatchesByMemberId(@Param("memberId") Long memberId);

}
