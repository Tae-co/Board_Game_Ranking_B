package com.board_game_back.Repository;

import com.board_game_back.Entity.Room;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByInviteCode(String inviteCode);
    boolean existsByInviteCode(String inviteCode);
    List<Room> findByCommunityId(Long communityId);
    long countByCommunityId(Long communityId);
    List<Room> findByBoardGameId(Long boardGameId);

    @Query("""
        SELECT r.communityId, COUNT(r)
        FROM Room r
        WHERE r.communityId IN :communityIds
        GROUP BY r.communityId
        """)
    List<Object[]> countByCommunityIds(@Param("communityIds") Collection<Long> communityIds);
}
