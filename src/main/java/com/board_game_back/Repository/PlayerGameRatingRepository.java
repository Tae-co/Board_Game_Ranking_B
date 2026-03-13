package com.board_game_back.Repository;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerGameRatingRepository extends JpaRepository<PlayerGameRating, Long> {

    List<PlayerGameRating> findByBoardGameIdOrderByGameStatsRatingDesc(Long boardGameId);

    Optional<PlayerGameRating> findByMemberAndBoardGameAndRoom(Member member, BoardGame boardGame, Room room);

    Optional<PlayerGameRating> findByMemberAndBoardGame(Member member, BoardGame boardGame);

    List<PlayerGameRating> findByRoomIdAndBoardGameIdOrderByGameStatsRatingDesc(Long roomId, Long boardGameId);

    List<PlayerGameRating> findByRoomIdAndBoardGameIdAndPlayCountGreaterThanEqualOrderByGameStatsRatingDesc(
        Long roomId, Long boardGameId, int minPlayCount);

    // 멤버ID + 방ID로 조회
    Optional<PlayerGameRating> findByMember_IdAndRoom_Id(Long memberId, Long roomId);

    // 방별 랭킹 - 플레이한 멤버 먼저, 그 다음 미플레이 멤버 (rating 내림차순)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM PlayerGameRating p WHERE p.room.id = :roomId AND p.boardGame.id = :boardGameId ORDER BY CASE WHEN p.playCount > 0 THEN 0 ELSE 1 END ASC, p.gameStats.rating DESC")
    List<PlayerGameRating> findByRoomIdAndBoardGameIdOrderByPlayedThenRating(@org.springframework.data.repository.query.Param("roomId") Long roomId, @org.springframework.data.repository.query.Param("boardGameId") Long boardGameId);

    // 방 삭제 시 FK 제거용
    void deleteByRoomId(Long roomId);
}
