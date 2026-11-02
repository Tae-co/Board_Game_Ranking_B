package com.board_game_back.Repository;

import com.board_game_back.Entity.ScoreSheetRequestEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScoreSheetRequestEntryRepository extends JpaRepository<ScoreSheetRequestEntry, Long> {

    Optional<ScoreSheetRequestEntry> findByGameRequestIdAndMemberIdAndKind(Long gameRequestId, Long memberId,
                                                                          ScoreSheetRequestEntry.Kind kind);

    List<ScoreSheetRequestEntry> findByMemberIdAndKindOrderByCreatedAtDesc(Long memberId, ScoreSheetRequestEntry.Kind kind);

    @Modifying
    @Query("UPDATE ScoreSheetRequestEntry e SET e.memberId = NULL WHERE e.memberId = :memberId")
    void anonymizeMember(@Param("memberId") Long memberId);
}
