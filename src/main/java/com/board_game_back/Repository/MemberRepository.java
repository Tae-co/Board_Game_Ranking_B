package com.board_game_back.Repository;

import com.board_game_back.Entity.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findBySocialId(String socialId);

    boolean existsByNickname(String nickname);

    List<Member> findByNicknameContainingIgnoreCase(String nickname);
}
