package com.board_game_back.Repository;

import com.board_game_back.Entity.RoomSeason;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomSeasonRepository extends JpaRepository<RoomSeason, Long> {

    /** 진행 중 시즌. 방당 하나 (uq_room_season_open). */
    Optional<RoomSeason> findByRoomIdAndClosedAtIsNull(Long roomId);

    /** 마감된 시즌, 최신순 */
    List<RoomSeason> findByRoomIdAndClosedAtIsNotNullOrderBySeasonNumberDesc(Long roomId);

    boolean existsByRoomIdAndClosedAtIsNotNull(Long roomId);

    /**
     * 롤오버 직전에 잠그고 읽는다. 정각 스케줄러와 경기 등록이 같은 시즌을 동시에 넘기면 둘째가
     * uq_room_season_open에 걸려 그 경기 등록이 500이 된다 — 잠금으로 줄을 세우면 둘째는 닫힌 행을 보고 그냥 지나간다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM RoomSeason s WHERE s.roomId = :roomId AND s.closedAt IS NULL")
    Optional<RoomSeason> findOpenForUpdate(@Param("roomId") Long roomId);

    @Query("SELECT s FROM RoomSeason s WHERE s.closedAt IS NULL AND s.endAt <= :now")
    List<RoomSeason> findDue(@Param("now") LocalDateTime now);

    List<RoomSeason> findByIdIn(Collection<Long> ids);
}
