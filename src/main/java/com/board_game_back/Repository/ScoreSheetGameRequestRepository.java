package com.board_game_back.Repository;

import com.board_game_back.Entity.ScoreSheetGameRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScoreSheetGameRequestRepository extends JpaRepository<ScoreSheetGameRequest, Long> {

    Optional<ScoreSheetGameRequest> findByNormalizedName(String normalizedName);
}
