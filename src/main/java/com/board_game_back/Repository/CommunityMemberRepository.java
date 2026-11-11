package com.board_game_back.Repository;

import com.board_game_back.Entity.CommunityMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {

    boolean existsByCommunityIdAndMemberId(Long communityId, Long memberId);

    long countByCommunityId(Long communityId);

    Optional<CommunityMember> findByCommunityIdAndMemberId(Long communityId, Long memberId);

    @Query("SELECT cm.community.id FROM CommunityMember cm WHERE cm.member.id = :memberId")
    List<Long> findCommunityIdsByMemberId(@Param("memberId") Long memberId);

    // member·community는 EAGER라 그냥 조회하면 멤버 수만큼 member SELECT가 따로 나간다(N+1). 한 번에 조인해 가져온다.
    // 조인하면 DB가 주는 순서가 바뀌므로, 화면들이 보던 가입 순서(id)를 명시한다
    @EntityGraph(attributePaths = {"member", "community"})
    @Query("SELECT cm FROM CommunityMember cm WHERE cm.community.id = :communityId ORDER BY cm.id")
    List<CommunityMember> findByCommunityId(@Param("communityId") Long communityId);

    @Query("""
        SELECT cm.community.id, COUNT(cm)
        FROM CommunityMember cm
        WHERE cm.community.id IN :communityIds
        GROUP BY cm.community.id
        """)
    List<Object[]> countByCommunityIds(@Param("communityIds") Collection<Long> communityIds);

    void deleteByCommunityId(Long communityId);

    @Modifying
    @Query("DELETE FROM CommunityMember cm WHERE cm.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
