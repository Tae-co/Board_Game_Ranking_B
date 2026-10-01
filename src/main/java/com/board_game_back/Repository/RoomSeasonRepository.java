package com.board_game_back.Repository;

import com.board_game_back.Entity.RoomSeason;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomSeasonRepository extends JpaRepository<RoomSeason, Long> {

    /** 진행 중 시즌. 방당 하나 (uq_room_season_open). */
    Optional<RoomSeason> findByRoomIdAndClosedAtIsNull(Long roomId);

    /** 마감된 시즌, 최신순 */
    List<RoomSeason> findByRoomIdAndClosedAtIsNotNullOrderBySeasonNumberDesc(Long roomId);

    boolean existsByRoomIdAndClosedAtIsNotNull(Long roomId);

    @Query("SELECT s FROM RoomSeason s WHERE s.closedAt IS NULL AND s.endAt <= :now")
    List<RoomSeason> findDue(@Param("now") LocalDateTime now);

    List<RoomSeason> findByIdIn(Collection<Long> ids);
}
