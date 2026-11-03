package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreContestEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoreContestEntryRepository extends JpaRepository<StoreContestEntry, Long> {

    List<StoreContestEntry> findByContestIdOrderByAppliedAtAscIdAsc(Long contestId);

    List<StoreContestEntry> findByContestIdIn(Collection<Long> contestIds);

    Optional<StoreContestEntry> findByContestIdAndMemberId(Long contestId, Long memberId);

    List<StoreContestEntry> findByMemberIdAndStatusIn(Long memberId, Collection<StoreContestEntry.Status> statuses);

    void deleteByContestId(Long contestId);

    @Modifying
    @Query("UPDATE StoreContestEntry e SET e.memberId = NULL WHERE e.memberId = :memberId")
    void anonymizeMember(@Param("memberId") Long memberId);
}
