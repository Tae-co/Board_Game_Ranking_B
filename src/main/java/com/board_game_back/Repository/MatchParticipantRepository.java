package com.board_game_back.Repository;

import com.board_game_back.Entity.MatchParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

    @Modifying
    @Query("DELETE FROM MatchParticipant mp WHERE mp.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
