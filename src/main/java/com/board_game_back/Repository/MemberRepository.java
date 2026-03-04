package com.board_game_back.Repository;

import com.board_game_back.Entity.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 로그인 ID(username)로 유저 찾기
    Optional<Member> findByUsername(String username);

    // 로그인 ID(phoneNumber)로 유저 찾기
    Optional<Member> findByPhoneNumber(String phoneNumber);

    // 전체 종합 랭킹(overallRating) 순으로 유저 목록 조회 (리더보드용)
    List<Member> findAllByOrderByOverallStatsRatingDesc();
}
