package com.board_game_back.Repository;

import com.board_game_back.Entity.MatchRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {

    @Query("""
        SELECT DISTINCT m FROM MatchRecord m
        JOIN FETCH m.boardGame
        JOIN FETCH m.participants p
        JOIN FETCH p.member
        WHERE m.room.id = :roomId
        ORDER BY m.playedAt DESC
        """)
    List<MatchRecord> findByRoomIdWithParticipants(@Param("roomId") Long roomId);

    @Query("""
        SELECT DISTINCT m FROM MatchRecord m
        JOIN FETCH m.boardGame
        JOIN FETCH m.participants p
        JOIN FETCH p.member
        WHERE m.room.id = :roomId AND m.boardGame.id = :boardGameId
        ORDER BY m.playedAt DESC
        """)
    List<MatchRecord> findByRoomIdAndBoardGameIdWithParticipants(
        @Param("roomId") Long roomId, @Param("boardGameId") Long boardGameId);

    @Query("""
        SELECT DISTINCT m FROM MatchRecord m
        JOIN FETCH m.boardGame
        JOIN FETCH m.participants p
        JOIN FETCH p.member
        WHERE m.room.id = :roomId AND m.boardGame.id = :boardGameId
        ORDER BY m.playedAt ASC
        """)
    List<MatchRecord> findByRoomIdAndBoardGameIdWithParticipantsAsc(
        @Param("roomId") Long roomId, @Param("boardGameId") Long boardGameId);

    void deleteByRoomId(Long roomId);

    @Query("SELECT DISTINCT m.room.id, m.boardGame.id FROM MatchRecord m WHERE m.room IS NOT NULL")
    List<Object[]> findDistinctRoomBoardGamePairs();
}
