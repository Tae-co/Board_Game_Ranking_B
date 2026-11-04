package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreEventUse;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreEventUseRepository extends JpaRepository<StoreEventUse, Long> {

    Optional<StoreEventUse> findByEventIdAndMemberId(Long eventId, Long memberId);

    List<StoreEventUse> findByMemberIdAndEventIdIn(Long memberId, Collection<Long> eventIds);
}
