package com.board_game_back.Repository;

import com.board_game_back.Entity.EventName;
import com.board_game_back.Entity.UserEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEventRepository extends JpaRepository<UserEvent, Long> {

    boolean existsByMemberIdAndCommunityIdAndEventName(Long memberId, Long communityId, EventName eventName);
}
