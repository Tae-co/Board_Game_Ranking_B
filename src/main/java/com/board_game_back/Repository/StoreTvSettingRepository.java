package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreTvSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreTvSettingRepository extends JpaRepository<StoreTvSetting, Long> {
}
