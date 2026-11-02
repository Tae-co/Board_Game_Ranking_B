package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoreRatingRepository extends JpaRepository<StoreRating, Long> {

    Optional<StoreRating> findByStoreIdAndBoardGameIdAndMemberId(Long storeId, Long boardGameId, Long memberId);

    List<StoreRating> findByStoreIdAndBoardGameId(Long storeId, Long boardGameId);

    List<StoreRating> findByStoreIdAndBoardGameIdIn(Long storeId, Collection<Long> boardGameIds);

    List<StoreRating> findByStoreIdAndMemberId(Long storeId, Long memberId);

    @Modifying
    @Query("DELETE FROM StoreRating r WHERE r.memberId = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);

    @Modifying
    @Query("DELETE FROM StoreRating r WHERE r.storeId = :storeId AND r.memberId = :memberId")
    void deleteByStoreIdAndMemberId(@Param("storeId") Long storeId, @Param("memberId") Long memberId);
}
