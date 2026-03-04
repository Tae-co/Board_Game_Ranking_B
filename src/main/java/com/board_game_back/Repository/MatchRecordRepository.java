package com.board_game_back.Repository;

import com.board_game_back.Entity.MatchRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {
    // 특정 보드게임의 최근 매치 기록 가져오기
    List<MatchRecord> findByBoardGameIdOrderByPlayedAtDesc(Long boardGameId);

}
