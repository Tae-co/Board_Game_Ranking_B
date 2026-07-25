package com.board_game_back.Repository;

import com.board_game_back.Entity.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CommunityRepository extends JpaRepository<Community, Long> {
    Optional<Community> findByCreatedBy(Long memberId);
    List<Community> findAllByCreatedBy(Long memberId);
    Optional<Community> findByInviteCode(String inviteCode);
    boolean existsByInviteCode(String inviteCode);
}
