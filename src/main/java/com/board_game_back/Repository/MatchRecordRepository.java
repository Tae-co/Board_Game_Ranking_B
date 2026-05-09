package com.board_game_back.Repository;

import com.board_game_back.Entity.MatchRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {

    List<MatchRecord> findByBoardGameIdOrderByPlayedAtDesc(Long boardGameId);

    List<MatchRecord> findByRoomIdOrderByPlayedAtDesc(Long roomId);

    List<MatchRecord> findByRoomIdAndBoardGameIdOrderByPlayedAtAsc(Long roomId, Long boardGameId);

    List<MatchRecord> findByRoomIdAndBoardGameIdOrderByPlayedAtDesc(Long roomId, Long boardGameId);

    void deleteByRoomId(Long roomId);

    @Query("SELECT DISTINCT m.room.id, m.boardGame.id FROM MatchRecord m WHERE m.room IS NOT NULL")
    List<Object[]> findDistinctRoomBoardGamePairs();
}
