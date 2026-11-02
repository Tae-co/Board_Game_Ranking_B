package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreSeason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreSeasonRepository extends JpaRepository<StoreSeason, Long> {

    List<StoreSeason> findByStoreIdOrderByStartDateAsc(Long storeId);

    List<StoreSeason> findByStoreIdAndFinalizedAtIsNotNullOrderByStartDateDesc(Long storeId);
}
