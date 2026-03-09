package com.board_game_back.Repository;

import com.board_game_back.Entity.MatchRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {
    List<MatchRecord> findByBoardGameIdOrderByPlayedAtDesc(Long boardGameId);
    List<MatchRecord> findByRoomIdOrderByPlayedAtDesc(Long roomId);
    List<MatchRecord> findByRoomIdAndBoardGameIdOrderByPlayedAtAsc(Long roomId, Long boardGameId);
}
