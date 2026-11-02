package com.board_game_back.Repository;

import com.board_game_back.Entity.StorePartyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StorePartyMemberRepository extends JpaRepository<StorePartyMember, Long> {

    Optional<StorePartyMember> findByMemberIdAndLeftAtIsNull(Long memberId);

    List<StorePartyMember> findByPartyIdAndLeftAtIsNullOrderByJoinedAtAscIdAsc(Long partyId);

    List<StorePartyMember> findByPartyIdAndLeftReasonAndLeftAtAfter(Long partyId, String leftReason, LocalDateTime after);
}
