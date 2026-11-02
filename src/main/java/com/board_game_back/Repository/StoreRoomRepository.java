package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StoreRoomRepository extends JpaRepository<StoreRoom, Long> {

    List<StoreRoom> findByStoreIdAndClosedFalse(Long storeId);

    @Query("""
        SELECT sr.storeId, COUNT(sr)
        FROM StoreRoom sr
        WHERE sr.storeId IN :storeIds AND sr.closed = false
        GROUP BY sr.storeId
        """)
    List<Object[]> countOpenByStoreIds(@Param("storeIds") Collection<Long> storeIds);

    @Modifying
    @Query("DELETE FROM StoreRoom sr WHERE sr.boardGameId = :boardGameId")
    void deleteByBoardGameId(@Param("boardGameId") Long boardGameId);
}
