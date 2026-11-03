package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreContest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoreContestRepository extends JpaRepository<StoreContest, Long> {

    /** 신청·도착 확인·시작·판 확인을 직렬화한다 (정원 넘침, 시작 두 번) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM StoreContest c WHERE c.id = :id")
    Optional<StoreContest> findByIdForUpdate(@Param("id") Long id);

    List<StoreContest> findByStoreIdOrderByStartsAtDesc(Long storeId);

    List<StoreContest> findByStoreIdAndStatusIn(Long storeId, Collection<StoreContest.Status> statuses);

    List<StoreContest> findByStatus(StoreContest.Status status);

    List<StoreContest> findByStoreIdAndBusinessDateBetweenOrderByStartsAtAsc(Long storeId, LocalDate from, LocalDate to);

    long countByStoreRoomIdAndStatus(Long storeRoomId, StoreContest.Status status);

    @Modifying
    @Query("UPDATE StoreContest c SET c.recorderMemberId = NULL WHERE c.recorderMemberId = :memberId")
    void anonymizeRecorder(@Param("memberId") Long memberId);
}
