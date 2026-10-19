package com.board_game_back.Repository;

import com.board_game_back.Entity.UserEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserEventRepository extends JpaRepository<UserEvent, Long> {

    /**
     * 커뮤니티 멤버별 앱 접속(APP_OPENED) 수. [memberId, count], 많은 순.
     *
     * <p>앱을 켤 때 액세스 토큰이 만료돼 있으면 member_id 없이 찍힌다. 같은 기기(anon_id)가 다른 이벤트에서
     * 회원 한 명으로 찍혔으면 그 회원의 접속으로 본다. 여러 회원이 쓴 기기는 누구인지 몰라 뺀다.
     */
    @Query(value = """
        WITH device AS (
            SELECT anon_id, MIN(member_id) AS member_id FROM user_event
            WHERE member_id IS NOT NULL AND anon_id IS NOT NULL
            GROUP BY anon_id HAVING COUNT(DISTINCT member_id) = 1
        )
        SELECT COALESCE(e.member_id, d.member_id) AS mid, COUNT(*) AS cnt
        FROM user_event e
        LEFT JOIN device d ON e.member_id IS NULL AND d.anon_id = e.anon_id
        JOIN community_member cm ON cm.member_id = COALESCE(e.member_id, d.member_id)
        WHERE e.event_name = 'APP_OPENED' AND cm.community_id = :communityId
        GROUP BY mid
        ORDER BY cnt DESC, mid ASC
        """, nativeQuery = true)
    List<Object[]> countAppOpensByCommunityMember(@Param("communityId") Long communityId);
}
