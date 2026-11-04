package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreReportRepository extends JpaRepository<StoreReport, Long> {

    Optional<StoreReport> findByStoreMatchIdAndReporterMemberId(Long storeMatchId, Long reporterMemberId);

    List<StoreReport> findByStoreIdOrderByCreatedAtDesc(Long storeId);

    List<StoreReport> findByResolutionBatch(String batch);

    @Modifying
    @Query("UPDATE StoreReport r SET r.reporterMemberId = NULL WHERE r.reporterMemberId = :memberId")
    void anonymizeReporter(@Param("memberId") Long memberId);

    @Modifying
    @Query("UPDATE StoreReport r SET r.targetMemberId = NULL WHERE r.targetMemberId = :memberId")
    void anonymizeTarget(@Param("memberId") Long memberId);
}
