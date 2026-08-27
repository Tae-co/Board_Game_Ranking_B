package com.board_game_back.Repository;

import com.board_game_back.Entity.MatchRecord;
import java.time.LocalDateTime;
import java.util.Collection;
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

    boolean existsByBoardGameId(Long boardGameId);

    @Query("SELECT DISTINCT m.room.id, m.boardGame.id FROM MatchRecord m WHERE m.room IS NOT NULL")
    List<Object[]> findDistinctRoomBoardGamePairs();

    // ── 시즌 결산 (커뮤니티에 속한 방들의 경기를 월 단위로 집계) ──

    @Query("SELECT m.playedAt FROM MatchRecord m WHERE m.room.id IN :roomIds")
    List<LocalDateTime> findPlayedAtByRoomIds(@Param("roomIds") Collection<Long> roomIds);

    @Query("""
        SELECT DISTINCT m FROM MatchRecord m
        JOIN FETCH m.boardGame
        JOIN FETCH m.participants p
        JOIN FETCH p.member
        WHERE m.room.id IN :roomIds AND m.playedAt >= :from AND m.playedAt < :to
        ORDER BY m.playedAt ASC
        """)
    List<MatchRecord> findByRoomIdsAndPlayedAtRange(
        @Param("roomIds") Collection<Long> roomIds,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    /** 커뮤니티 내 멤버별 첫 경기 시각 (다크호스 = 이번 시즌에 처음 뛴 멤버) */
    @Query("""
        SELECT p.member.id, MIN(m.playedAt) FROM MatchRecord m
        JOIN m.participants p
        WHERE m.room.id IN :roomIds
        GROUP BY p.member.id
        """)
    List<Object[]> findFirstPlayedAtByMember(@Param("roomIds") Collection<Long> roomIds);
}
