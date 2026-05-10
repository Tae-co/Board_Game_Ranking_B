package com.board_game_back.Repository;

import com.board_game_back.Entity.BoardGame;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardGameRepository extends JpaRepository<BoardGame, Long> {

    List<BoardGame> findByIdIn(Collection<Long> ids);
}
