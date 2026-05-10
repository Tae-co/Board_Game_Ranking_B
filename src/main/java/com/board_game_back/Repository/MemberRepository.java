package com.board_game_back.Repository;

import com.board_game_back.Entity.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByUsername(String username);

    Optional<Member> findByPhoneNumber(String phoneNumber);

    Optional<Member> findBySocialId(String socialId);

    Optional<Member> findByNickname(String nickname);

    boolean existsByNickname(String nickname);

    List<Member> findByNicknameContainingIgnoreCase(String nickname);
}
