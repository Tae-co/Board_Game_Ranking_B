package com.board_game_back.Repository;

import com.board_game_back.Entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByClosedAtIsNull();
}
