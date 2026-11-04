package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface StoreEventRepository extends JpaRepository<StoreEvent, Long> {

    List<StoreEvent> findByStoreIdOrderByStartsOnDesc(Long storeId);

    /** 기간이 date 이후까지 이어지는 이벤트 (진행 중 + 예정) */
    List<StoreEvent> findByStoreIdAndEndsOnGreaterThanEqualOrderByStartsOnAsc(Long storeId, LocalDate date);

    /** 리포트: 기간이 [from, to]와 겹치는 이벤트 */
    List<StoreEvent> findByStoreIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqualOrderByStartsOnAsc(
        Long storeId, LocalDate to, LocalDate from);

    void deleteByStoreIdAndEndsOnGreaterThanEqual(Long storeId, LocalDate date);
}
