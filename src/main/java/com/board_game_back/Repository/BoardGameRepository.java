package com.board_game_back.Repository;

import com.board_game_back.Entity.BoardGame;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardGameRepository extends JpaRepository<BoardGame, Long> {

    List<BoardGame> findByIdIn(Collection<Long> ids);

    // 공식 게임만 (커뮤니티 지정 없이 조회할 때)
    List<BoardGame> findByCommunityIdIsNull();

    // 공식 게임 + 해당 커뮤니티가 만든 커스텀 게임
    List<BoardGame> findByCommunityIdIsNullOrCommunityId(Long communityId);

    boolean existsByCommunityIdAndName(Long communityId, String name);
}
