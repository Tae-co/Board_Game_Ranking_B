package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreSeasonSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StoreSeasonSnapshotRepository extends JpaRepository<StoreSeasonSnapshot, Long> {

    List<StoreSeasonSnapshot> findBySeasonId(Long seasonId);

    List<StoreSeasonSnapshot> findBySeasonIdIn(Collection<Long> seasonIds);
}
