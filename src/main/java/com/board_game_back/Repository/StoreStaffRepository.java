package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreStaffRepository extends JpaRepository<StoreStaff, Long> {

    Optional<StoreStaff> findByMemberId(Long memberId);

    List<StoreStaff> findByStoreId(Long storeId);

    @Query("SELECT s.memberId FROM StoreStaff s")
    List<Long> findAllMemberIds();

    @Modifying
    @Query("DELETE FROM StoreStaff s WHERE s.memberId = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
