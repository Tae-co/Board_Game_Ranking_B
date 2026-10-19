package com.board_game_back.Repository;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
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

    @Query("SELECT p FROM PlayerGameRating p JOIN FETCH p.boardGame WHERE p.member.id = :memberId AND p.playCount > 0")
    List<PlayerGameRating> findPlayedByMemberId(@Param("memberId") Long memberId);

    /** 전체 랭킹(Member.overallStats) 계산용 — 칩 게임은 점수 단위가 달라 뺀다. */
    @Query("SELECT p FROM PlayerGameRating p JOIN FETCH p.boardGame b WHERE p.member.id = :memberId AND p.playCount > 0 AND b.ratingType = com.board_game_back.Entity.RatingType.ELO")
    List<PlayerGameRating> findPlayedEloByMemberId(@Param("memberId") Long memberId);

    /** 롤오버: 방의 모든 게임 레이팅을 한 번에. 스냅샷이 member·boardGame을 모두 읽는다. */
    @Query("SELECT p FROM PlayerGameRating p JOIN FETCH p.member JOIN FETCH p.boardGame WHERE p.room.id = :roomId")
    List<PlayerGameRating> findByRoomIdWithMemberAndBoardGame(@Param("roomId") Long roomId);

    /** 커뮤니티 현황 게임별 최고 점수: 방들의 진행 중 시즌 레이팅 중 minPlays판 이상 뛴 것. 화면이 방·게임 이름과 닉네임을 읽는다. */
    /** 칩 게임은 점수 단위가 달라 Elo 점수와 같이 줄 세우지 않는다. */
    @Query("SELECT p FROM PlayerGameRating p JOIN FETCH p.member JOIN FETCH p.room JOIN FETCH p.boardGame WHERE p.room.id IN :roomIds AND p.playCount >= :minPlays AND p.boardGame.ratingType = com.board_game_back.Entity.RatingType.ELO")
    List<PlayerGameRating> findByRoomIdsWithMinPlays(@Param("roomIds") List<Long> roomIds, @Param("minPlays") int minPlays);

    void deleteByRoomId(Long roomId);

    void deleteByMember_IdAndRoom_Id(Long memberId, Long roomId);

    void deleteByMember_Id(Long memberId);

    boolean existsByLastPlayedAtIsNullAndPlayCountGreaterThan(int playCount);
}
