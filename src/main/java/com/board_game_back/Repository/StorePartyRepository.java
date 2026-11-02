package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreParty;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StorePartyRepository extends JpaRepository<StoreParty, Long> {

    /** 판·그룹 변경을 직렬화한다 (시작 두 번 누름, 확인과 마감 겹침) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM StoreParty p WHERE p.id = :id")
    Optional<StoreParty> findByIdForUpdate(@Param("id") Long id);

    Optional<StoreParty> findByStoreIdAndCodeAndDisbandedAtIsNull(Long storeId, String code);

    boolean existsByStoreIdAndCodeAndDisbandedAtIsNull(Long storeId, String code);

    List<StoreParty> findByStoreIdAndDisbandedAtIsNull(Long storeId);

    List<StoreParty> findByDisbandedAtIsNull();
}
