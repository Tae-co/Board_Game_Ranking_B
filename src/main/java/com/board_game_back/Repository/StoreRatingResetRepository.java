package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreRatingReset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoreRatingResetRepository extends JpaRepository<StoreRatingReset, Long> {

    List<StoreRatingReset> findByStoreIdAndUndoneAtIsNullOrderByResetAtAsc(Long storeId);

    List<StoreRatingReset> findByBatch(String batch);

    List<StoreRatingReset> findByMemberIdAndUndoneAtIsNull(Long memberId);

    @Modifying
    @Query("UPDATE StoreRatingReset r SET r.memberId = NULL WHERE r.memberId = :memberId")
    void anonymizeMember(@Param("memberId") Long memberId);
}
