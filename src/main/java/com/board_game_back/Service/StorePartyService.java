package com.board_game_back.Service;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreMatch;
import com.board_game_back.Entity.StoreMatchParticipant;
import com.board_game_back.Entity.StoreMember;
import com.board_game_back.Entity.StoreParty;
import com.board_game_back.Entity.StorePartyMember;
import com.board_game_back.Entity.StorePartyMember.LeftReason;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.StoreMatchParticipantRepository;
import com.board_game_back.Repository.StoreMatchRepository;
import com.board_game_back.Repository.StoreMemberRepository;
import com.board_game_back.Repository.StorePartyMemberRepository;
import com.board_game_back.Repository.StorePartyRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Utils.StoreClock;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘의 그룹 (plan-store §2.1 3번, 2i·3b·3c). 내보내기·나가기·해산·기록자 넘기기는 판과 판 사이에만.
 * 기록자가 빠지면 가장 먼저 합류한 사람에게 넘어가고, 아무도 안 남으면 그룹이 없어진다.
 */
@Service
@RequiredArgsConstructor
public class StorePartyService {

    static final Set<StoreMatch.Status> OPEN = EnumSet.of(
        StoreMatch.Status.PLAYING, StoreMatch.Status.AWAITING_CONFIRM, StoreMatch.Status.DISPUTED);
    private static final int NOTICE_MINUTES = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StoreRepository storeRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StorePartyRepository storePartyRepository;
    private final StorePartyMemberRepository storePartyMemberRepository;
    private final StoreMatchRepository storeMatchRepository;
    private final StoreMatchParticipantRepository storeMatchParticipantRepository;
    private final BoardGameRepository boardGameRepository;
    private final MemberRepository memberRepository;
    private final StoreAccessService storeAccessService;
    private final StorePartyNotifier notifier;

    static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    // ── 만들기 · 합류 ──

    @Transactional
    public StoreDto.PartyResponse create(Long storeId, Long memberId) {
        return create(storeId, memberId, now());
    }

    @Transactional
    StoreDto.PartyResponse create(Long storeId, Long memberId, LocalDateTime now) {
        Store store = requirePlayable(storeId, memberId, now);
        leaveCurrentForMove(memberId, null, now);
        StoreParty party = storePartyRepository.save(
            new StoreParty(storeId, newCode(storeId), memberId, StoreClock.businessDate(store, now), now));
        storePartyMemberRepository.save(new StorePartyMember(party.getId(), memberId, now));
        return response(party, now);
    }

    /** move = 이미 다른 그룹에 있을 때 "옮길까요?"에 옮기기를 눌렀다 (3c) */
    @Transactional
    public StoreDto.PartyResponse join(Long storeId, Long memberId, String code, boolean move) {
        return join(storeId, memberId, code, move, now());
    }

    @Transactional
    StoreDto.PartyResponse join(Long storeId, Long memberId, String code, boolean move, LocalDateTime now) {
        requirePlayable(storeId, memberId, now);
        StoreParty target = storePartyRepository.findByStoreIdAndCodeAndDisbandedAtIsNull(storeId, code == null ? "" : code.trim())
            .orElseThrow(() -> new StoreException(StoreErrorCode.PARTY_NOT_FOUND));
        StorePartyMember current = storePartyMemberRepository.findByMemberIdAndLeftAtIsNull(memberId).orElse(null);
        if (current != null && current.getPartyId().equals(target.getId())) return response(target, now);
        if (current != null && !move) throw new StoreException(StoreErrorCode.ALREADY_IN_PARTY);
        leaveCurrentForMove(memberId, target.getId(), now);

        StoreParty locked = lock(target.getId());
        if (!locked.isActive()) throw new StoreException(StoreErrorCode.PARTY_NOT_FOUND);
        storePartyMemberRepository.save(new StorePartyMember(locked.getId(), memberId, now));
        notifier.changed(locked.getId());
        return response(locked, now);
    }

    /** 내 그룹. 없으면 empty (주사위 버튼이 안 뜸) */
    public Optional<StoreDto.PartyResponse> current(Long memberId) {
        LocalDateTime now = now();
        return storePartyMemberRepository.findByMemberIdAndLeftAtIsNull(memberId)
            .flatMap(pm -> storePartyRepository.findById(pm.getPartyId()))
            .filter(StoreParty::isActive)
            .map(p -> response(p, now));
    }

    // ── 2i 그룹 관리 ──

    @Transactional
    public void leave(Long memberId) {
        LocalDateTime now = now();
        StorePartyMember me = requireMembership(memberId);
        StoreParty party = lock(me.getPartyId());
        requireNoOpenMatch(party.getId());
        removeMember(party, me, LeftReason.LEAVE, now);
    }

    @Transactional
    public StoreDto.PartyResponse kick(Long memberId, Long targetMemberId) {
        LocalDateTime now = now();
        StorePartyMember me = requireMembership(memberId);
        StoreParty party = lock(me.getPartyId());
        requireNoOpenMatch(party.getId());
        StorePartyMember target = activeMembers(party.getId()).stream()
            .filter(pm -> targetMemberId.equals(pm.getMemberId()))
            .findFirst()
            .orElseThrow(() -> new StoreException(StoreErrorCode.NOT_IN_PARTY));
        removeMember(party, target, LeftReason.KICK, now);
        return response(party, now);
    }

    @Transactional
    public void disband(Long memberId) {
        LocalDateTime now = now();
        StorePartyMember me = requireMembership(memberId);
        StoreParty party = lock(me.getPartyId());
        requireNoOpenMatch(party.getId());
        for (StorePartyMember pm : activeMembers(party.getId())) pm.leave(LeftReason.DISBAND, now);
        party.disband(now);
        notifier.changed(party.getId());
    }

    /** 기록자만, 판 사이에만 (31차: 게임 중엔 기록자를 바꿀 수 없다) */
    @Transactional
    public StoreDto.PartyResponse handOver(Long memberId, Long targetMemberId) {
        LocalDateTime now = now();
        StorePartyMember me = requireMembership(memberId);
        StoreParty party = lock(me.getPartyId());
        if (!memberId.equals(party.getRecorderMemberId())) throw new StoreException(StoreErrorCode.NOT_RECORDER);
        requireNoOpenMatch(party.getId());
        boolean inParty = activeMembers(party.getId()).stream().anyMatch(pm -> targetMemberId.equals(pm.getMemberId()));
        if (!inParty) throw new StoreException(StoreErrorCode.NOT_IN_PARTY);
        party.changeRecorder(targetMemberId);
        notifier.changed(party.getId());
        return response(party, now);
    }

    // ── 자동으로 빠지기 (스케줄러·매장 나가기·탈퇴) ──

    /** 체크인이 끝난 사람을 그룹에서 뺀다. 판이 열려 있는 그룹은 건드리지 않는다 (판 중엔 만료 안 됨) */
    @Transactional
    public int expireCheckins(Long partyId, LocalDateTime now) {
        StoreParty party = lock(partyId);
        if (!party.isActive() || openMatch(partyId).isPresent()) return 0;
        int removed = 0;
        for (StorePartyMember pm : activeMembers(partyId)) {
            boolean checkedIn = storeMemberRepository.findByStoreIdAndMemberId(party.getStoreId(), pm.getMemberId())
                .map(sm -> sm.isCheckedIn(now))
                .orElse(false);
            if (!checkedIn) {
                removeMember(party, pm, LeftReason.EXPIRED, now);
                removed++;
            }
        }
        return removed;
    }

    /** 매장 나가기(1c). 이 매장 그룹에 있으면 빠진다. 판이 열려 있으면 막는다 */
    @Transactional
    public void leaveStore(Long storeId, Long memberId) {
        StorePartyMember pm = storePartyMemberRepository.findByMemberIdAndLeftAtIsNull(memberId).orElse(null);
        if (pm == null) return;
        StoreParty party = lock(pm.getPartyId());
        if (!party.getStoreId().equals(storeId)) return;
        requireNoOpenMatch(party.getId());
        removeMember(party, pm, LeftReason.LEAVE, now());
    }

    /** 회원 탈퇴. 그룹에서 빠지고 판 기록은 '나간 멤버'로 남는다 */
    @Transactional
    public void onMemberDeleted(Long memberId) {
        storePartyMemberRepository.findByMemberIdAndLeftAtIsNull(memberId).ifPresent(pm -> {
            StoreParty party = lock(pm.getPartyId());
            removeMember(party, pm, LeftReason.WITHDRAW, now());
        });
        storeMatchParticipantRepository.anonymizeMember(memberId);
        storeMatchRepository.anonymizeRecorder(memberId);
    }

    // ── 공통 (StoreMatchService도 쓴다) ──

    StoreParty lock(Long partyId) {
        return storePartyRepository.findByIdForUpdate(partyId)
            .orElseThrow(() -> new StoreException(StoreErrorCode.NOT_IN_PARTY));
    }

    Optional<StoreParty> findParty(Long partyId) {
        return storePartyRepository.findById(partyId);
    }

    List<StorePartyMember> activeMembers(Long partyId) {
        return storePartyMemberRepository.findByPartyIdAndLeftAtIsNullOrderByJoinedAtAscIdAsc(partyId);
    }

    Optional<StoreMatch> openMatch(Long partyId) {
        return storeMatchRepository.findFirstByPartyIdAndStatusIn(partyId, OPEN);
    }

    Optional<StorePartyMember> membership(Long memberId) {
        return storePartyMemberRepository.findByMemberIdAndLeftAtIsNull(memberId);
    }

    List<String> expiredNicknames(Long partyId, LocalDateTime now) {
        List<Long> ids = storePartyMemberRepository
            .findByPartyIdAndLeftReasonAndLeftAtAfter(partyId, LeftReason.EXPIRED.name(), now.minusMinutes(NOTICE_MINUTES))
            .stream().map(StorePartyMember::getMemberId).toList();
        Map<Long, String> names = nicknames(ids);
        return ids.stream().map(names::get).filter(n -> n != null).distinct().toList();
    }

    StoreDto.PartyBrief brief(StoreParty party) {
        List<Long> ids = activeMembers(party.getId()).stream().map(StorePartyMember::getMemberId).toList();
        Map<Long, String> names = nicknames(ids);
        return new StoreDto.PartyBrief(party.getId(), ids.size(), ids.stream().map(names::get).toList(),
            party.getRecorderMemberId(), party.getRecorderMemberId() == null ? null : names.get(party.getRecorderMemberId()));
    }

    Map<Long, String> nicknames(Collection<Long> memberIds) {
        return memberRepository.findAllById(memberIds.stream().filter(id -> id != null).toList()).stream()
            .collect(Collectors.toMap(Member::getId, Member::getNickname, (a, b) -> a));
    }

    private void removeMember(StoreParty party, StorePartyMember pm, LeftReason reason, LocalDateTime now) {
        pm.leave(reason, now);
        List<StorePartyMember> rest = activeMembers(party.getId()).stream()
            .filter(other -> !other.getId().equals(pm.getId()))
            .toList();
        if (rest.isEmpty()) {
            party.disband(now);
        } else if (pm.getMemberId() != null && pm.getMemberId().equals(party.getRecorderMemberId())) {
            party.changeRecorder(rest.get(0).getMemberId());
        }
        notifier.changed(party.getId());
    }

    /** 다른 그룹에 있으면 그 그룹에서 빠진다 (옮기기·새로 만들기). 그 그룹이 게임 중이면 막는다 */
    private void leaveCurrentForMove(Long memberId, Long targetPartyId, LocalDateTime now) {
        StorePartyMember current = storePartyMemberRepository.findByMemberIdAndLeftAtIsNull(memberId).orElse(null);
        if (current == null || current.getPartyId().equals(targetPartyId)) return;
        StoreParty old = lock(current.getPartyId());
        requireNoOpenMatch(old.getId());
        removeMember(old, current, LeftReason.MOVE, now);
        storePartyMemberRepository.flush();
    }

    private Store requirePlayable(Long storeId, Long memberId, LocalDateTime now) {
        Store store = storeRepository.findById(storeId).orElseThrow(() -> new StoreException(StoreErrorCode.STORE_NOT_FOUND));
        if (store.isClosed()) throw new StoreException(StoreErrorCode.STORE_CLOSED);
        if (storeAccessService.isStaff(memberId)) throw new StoreException(StoreErrorCode.STAFF_CANNOT_PLAY);
        StoreMember member = storeMemberRepository.findByStoreIdAndMemberId(storeId, memberId)
            .orElseThrow(() -> new StoreException(StoreErrorCode.NOT_MEMBER));
        if (!member.isCheckedIn(now)) throw new StoreException(StoreErrorCode.NOT_CHECKED_IN);
        return store;
    }

    private StorePartyMember requireMembership(Long memberId) {
        return storePartyMemberRepository.findByMemberIdAndLeftAtIsNull(memberId)
            .orElseThrow(() -> new StoreException(StoreErrorCode.NOT_IN_PARTY));
    }

    private void requireNoOpenMatch(Long partyId) {
        if (openMatch(partyId).isPresent()) throw new StoreException(StoreErrorCode.PARTY_PLAYING);
    }

    private String newCode(Long storeId) {
        for (int i = 0; i < 100; i++) {
            String code = String.format("%04d", RANDOM.nextInt(10_000));
            if (!storePartyRepository.existsByStoreIdAndCodeAndDisbandedAtIsNull(storeId, code)) return code;
        }
        throw new IllegalStateException("그룹 코드를 만들지 못했어요");
    }

    private StoreDto.PartyResponse response(StoreParty party, LocalDateTime now) {
        Store store = storeRepository.findById(party.getStoreId()).orElseThrow();
        List<StorePartyMember> members = activeMembers(party.getId());
        Map<Long, String> names = nicknames(members.stream().map(StorePartyMember::getMemberId).toList());
        List<StoreDto.PartyMemberItem> items = members.stream()
            .map(pm -> new StoreDto.PartyMemberItem(pm.getMemberId(), names.get(pm.getMemberId()),
                StoreDto.iso(pm.getJoinedAt()), pm.getMemberId().equals(party.getRecorderMemberId())))
            .toList();
        StoreMatch open = openMatch(party.getId()).orElse(null);
        StoreMatch last = open != null ? open : storeMatchRepository.findFirstByPartyIdOrderByIdDesc(party.getId()).orElse(null);
        return new StoreDto.PartyResponse(party.getId(),
            new StoreDto.StoreBrief(store.getId(), store.getName(), store.getBranch()),
            party.getCode(), party.getRecorderMemberId(), items, expiredNicknames(party.getId(), now),
            matchBrief(open), matchBrief(last));
    }

    StoreDto.MatchBrief matchBrief(StoreMatch match) {
        if (match == null) return null;
        String gameName = boardGameRepository.findById(match.getBoardGameId()).map(BoardGame::getName).orElse(null);
        List<Long> participantIds = storeMatchParticipantRepository.findByStoreMatchIdOrderByIdAsc(match.getId()).stream()
            .map(StoreMatchParticipant::getMemberId)
            .filter(id -> id != null)
            .toList();
        return new StoreDto.MatchBrief(match.getId(), match.getStatus().name(), match.getStoreRoomId(),
            match.getBoardGameId(), gameName, participantIds);
    }
}
