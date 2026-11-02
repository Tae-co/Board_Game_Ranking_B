package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreMatch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoreMatchRepository extends JpaRepository<StoreMatch, Long> {

    @Query("SELECT m.partyId FROM StoreMatch m WHERE m.id = :id")
    Optional<Long> findPartyIdById(@Param("id") Long id);

    Optional<StoreMatch> findFirstByPartyIdAndStatusIn(Long partyId, Collection<StoreMatch.Status> statuses);

    Optional<StoreMatch> findFirstByPartyIdOrderByIdDesc(Long partyId);

    List<StoreMatch> findByStatusIn(Collection<StoreMatch.Status> statuses);

    List<StoreMatch> findByStoreRoomIdAndStatusOrderByEndedAtDesc(Long storeRoomId, StoreMatch.Status status, Pageable pageable);

    List<StoreMatch> findByStoreIdAndStatus(Long storeId, StoreMatch.Status status);

    @Query("""
        SELECT COUNT(m) FROM StoreMatch m
        WHERE m.storeId = :storeId AND m.status IN :statuses
          AND m.id IN (SELECT p.storeMatchId FROM StoreMatchParticipant p WHERE p.memberId = :memberId)
        """)
    long countByStoreIdAndMemberAndStatusIn(@Param("storeId") Long storeId, @Param("memberId") Long memberId,
                                           @Param("statuses") Collection<StoreMatch.Status> statuses);

    @Query("""
        SELECT m.storeRoomId, COUNT(m) FROM StoreMatch m
        WHERE m.storeId = :storeId AND m.status = com.board_game_back.Entity.StoreMatch.Status.RECORDED
        GROUP BY m.storeRoomId
        """)
    List<Object[]> countRecordedByRoom(@Param("storeId") Long storeId);

    @Modifying
    @Query("UPDATE StoreMatch m SET m.recorderMemberId = NULL WHERE m.recorderMemberId = :memberId")
    void anonymizeRecorder(@Param("memberId") Long memberId);
}
