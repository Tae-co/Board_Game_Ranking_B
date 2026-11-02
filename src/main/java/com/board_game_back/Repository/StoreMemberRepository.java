package com.board_game_back.Repository;

import com.board_game_back.Entity.StoreMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoreMemberRepository extends JpaRepository<StoreMember, Long> {

    Optional<StoreMember> findByStoreIdAndMemberId(Long storeId, Long memberId);

    List<StoreMember> findByMemberIdOrderByJoinedAtAsc(Long memberId);

    long countByStoreId(Long storeId);

    @Query("""
        SELECT sm.storeId, COUNT(sm)
        FROM StoreMember sm
        WHERE sm.storeId IN :storeIds
        GROUP BY sm.storeId
        """)
    List<Object[]> countByStoreIds(@Param("storeIds") Collection<Long> storeIds);

    @Modifying
    @Query("DELETE FROM StoreMember sm WHERE sm.memberId = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
