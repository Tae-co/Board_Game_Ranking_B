package com.board_game_back.Repository;

import com.board_game_back.Entity.CommunityAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface CommunityAdminRepository extends JpaRepository<CommunityAdmin, Long> {

    @Query("SELECT ca FROM CommunityAdmin ca JOIN FETCH ca.member WHERE ca.community.id = :communityId")
    List<CommunityAdmin> findByCommunityId(@Param("communityId") Long communityId);

    @Query("SELECT ca FROM CommunityAdmin ca JOIN FETCH ca.member WHERE ca.community.id IN :communityIds")
    List<CommunityAdmin> findByCommunityIdIn(@Param("communityIds") Collection<Long> communityIds);

    boolean existsByCommunityIdAndMemberId(Long communityId, Long memberId);

    int countByCommunityId(Long communityId);

    @Modifying
    @Query("DELETE FROM CommunityAdmin ca WHERE ca.community.id = :communityId")
    void deleteByCommunityId(@Param("communityId") Long communityId);

    @Modifying
    @Query("DELETE FROM CommunityAdmin ca WHERE ca.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
