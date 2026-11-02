package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreDeviceRepository extends JpaRepository<StoreDevice, Long> {

    Optional<StoreDevice> findByTokenHash(String tokenHash);

    List<StoreDevice> findByStoreIdAndRevokedAtIsNullOrderByIdAsc(Long storeId);

    long countByStoreIdAndRevokedAtIsNull(Long storeId);
}
