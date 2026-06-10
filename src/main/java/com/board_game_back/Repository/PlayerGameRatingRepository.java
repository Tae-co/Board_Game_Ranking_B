package com.board_game_back.Repository;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerGameRatingRepository extends JpaRepository<PlayerGameRating, Long> {

    @Query("SELECT p FROM PlayerGameRating p JOIN FETCH p.member WHERE p.boardGame.id = :boardGameId ORDER BY (p.gameStats.rating - 3 * p.gameStats.ratingDeviation) DESC")
    List<PlayerGameRating> findByBoardGameIdOrderByDisplayScoreDesc(@Param("boardGameId") Long boardGameId);

    Optional<PlayerGameRating> findByMemberAndBoardGameAndRoom(Member member, BoardGame boardGame, Room room);

    List<PlayerGameRating> findByRoomIdAndBoardGameIdOrderByGameStatsRatingDesc(Long roomId, Long boardGameId);

    Optional<PlayerGameRating> findByMember_IdAndRoom_Id(Long memberId, Long roomId);

    @Query("SELECT p FROM PlayerGameRating p JOIN FETCH p.member WHERE p.room.id = :roomId AND p.boardGame.id = :boardGameId ORDER BY CASE WHEN p.playCount > 0 THEN 0 ELSE 1 END ASC, (p.gameStats.rating - 3 * p.gameStats.ratingDeviation) DESC")
    List<PlayerGameRating> findByRoomIdAndBoardGameIdOrderByPlayedThenDisplayScore(@Param("roomId") Long roomId, @Param("boardGameId") Long boardGameId);

    @Query("SELECT p FROM PlayerGameRating p JOIN FETCH p.member WHERE p.room.id = :roomId AND p.boardGame.id = :boardGameId ORDER BY CASE WHEN p.playCount > 0 THEN 0 ELSE 1 END ASC, p.gameStats.rating DESC")
    List<PlayerGameRating> findByRoomIdAndBoardGameIdOrderByPlayedThenRating(@Param("roomId") Long roomId, @Param("boardGameId") Long boardGameId);

    @Query("SELECT p FROM PlayerGameRating p JOIN FETCH p.boardGame WHERE p.member.id = :memberId AND p.playCount > 0")
    List<PlayerGameRating> findPlayedByMemberId(@Param("memberId") Long memberId);

    void deleteByRoomId(Long roomId);

    void deleteByMember_IdAndRoom_Id(Long memberId, Long roomId);

    void deleteByMember_Id(Long memberId);

    @Query("SELECT p FROM PlayerGameRating p WHERE p.playCount > 0 AND (p.lastPlayedAt IS NULL OR p.lastPlayedAt < :cutoff)")
    List<PlayerGameRating> findStaleActiveRatings(@Param("cutoff") LocalDateTime cutoff);

    boolean existsByLastPlayedAtIsNullAndPlayCountGreaterThan(int playCount);
}
