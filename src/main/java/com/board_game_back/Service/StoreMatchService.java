package com.board_game_back.Service;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreMatch;
import com.board_game_back.Entity.StoreMatch.EndReason;
import com.board_game_back.Entity.StoreMatchParticipant;
import com.board_game_back.Entity.StoreMember;
import com.board_game_back.Entity.StoreParty;
import com.board_game_back.Entity.StorePartyMember;
import com.board_game_back.Entity.StoreRating;
import com.board_game_back.Entity.StoreRoom;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.StoreMatchParticipantRepository;
import com.board_game_back.Repository.StoreMatchRepository;
import com.board_game_back.Repository.StoreMemberRepository;
import com.board_game_back.Repository.StoreRatingRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Repository.StoreRoomRepository;
import com.board_game_back.Utils.StoreClock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매장 판 (plan-store §2.1 6번, §6.5, 3d~3n). 시간 판정은 전부 여기서 한다.
 *
 * <ul>
 *   <li>시작: 기록자만. 저장 마감 = min(시작 + 최대 시간, 영업 마감)을 시작 때 저장
 *   <li>처음 저장: 시작 + 최소 시간 ≤ 지금 ≤ 저장 마감. 다시 저장(점수가 달라요·점수 고치기 뒤)은 영업 마감 전이면 된다
 *   <li>저장하면 5분 확인. 전원 확인 또는 5분이 지나면 기록 → 매장 Elo
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class StoreMatchService {

    public static final Duration CONFIRM_WINDOW = Duration.ofMinutes(5);
    private static final int MAX_DRAFT_LENGTH = 64_000;

    private final StoreRepository storeRepository;
    private final StoreRoomRepository storeRoomRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreMatchRepository storeMatchRepository;
    private final StoreMatchParticipantRepository participantRepository;
    private final StoreRatingRepository storeRatingRepository;
    private final BoardGameRepository boardGameRepository;
    private final StoreAccessService storeAccessService;
    private final StorePartyService partyService;
    private final StoreEloCalculator eloCalculator;
    private final StorePartyNotifier notifier;

    // ── 시작 ──

    /** 3번 하단 버튼 상태. null = 시작할 수 있음 */
    public String startBlock(Store store, StoreRoom room, BoardGame game, Long memberId, LocalDateTime now) {
        if (store.isClosed()) return "STORE_CLOSED";
        if (storeAccessService.isStaff(memberId)) return "STAFF";
        StorePartyMember me = partyService.membership(memberId).orElse(null);
        StoreParty party = me == null ? null : storeParty(me.getPartyId());
        if (party == null || !party.isActive() || !party.getStoreId().equals(store.getId())) return "NO_PARTY";
        if (!memberId.equals(party.getRecorderMemberId())) return "NOT_RECORDER";
        if (room.isClosed()) return "ROOM_CLOSED";
        if (!game.hasRecordMinutes()) return "GAME_TIME_NOT_SET";
        if (partyService.openMatch(party.getId()).isPresent()) return "PARTY_PLAYING";
        List<StorePartyMember> members = partyService.activeMembers(party.getId());
        if (members.size() < game.getMinPlayers() || members.size() > game.getMaxPlayers()) return "PLAYER_COUNT";
        if (!StoreClock.isOpen(store, now)) return "STORE_NOT_OPEN";
        LocalDateTime closesAt = StoreClock.closingAt(store, now);
        if (now.plusMinutes(game.getMinMinutes()).isAfter(closesAt)) return "TOO_CLOSE_TO_CLOSING";
        for (StorePartyMember pm : members) {
            boolean checkedIn = storeMemberRepository.findByStoreIdAndMemberId(store.getId(), pm.getMemberId())
                .map(sm -> sm.isCheckedIn(now)).orElse(false);
            if (!checkedIn) return pm.getMemberId().equals(memberId) ? "NOT_CHECKED_IN" : "MEMBER_NOT_CHECKED_IN";
        }
        return null;
    }

    @Transactional
    public StoreDto.MatchResponse start(Long storeId, Long storeRoomId, Long memberId) {
        return start(storeId, storeRoomId, memberId, StorePartyService.now());
    }

    @Transactional
    StoreDto.MatchResponse start(Long storeId, Long storeRoomId, Long memberId, LocalDateTime now) {
        Store store = storeRepository.findById(storeId).orElseThrow(() -> new StoreException(StoreErrorCode.STORE_NOT_FOUND));
        StoreRoom room = storeRoomRepository.findById(storeRoomId)
            .filter(r -> r.getStoreId().equals(storeId))
            .orElseThrow(() -> new StoreException(StoreErrorCode.ROOM_CLOSED));
        BoardGame game = boardGameRepository.findById(room.getBoardGameId()).orElseThrow();
        StorePartyMember me = partyService.membership(memberId).orElseThrow(() -> new StoreException(StoreErrorCode.NOT_IN_PARTY));
        StoreParty party = partyService.lock(me.getPartyId());

        String block = startBlock(store, room, game, memberId, now);
        if (block != null) throw new StoreException(blockError(block));

        LocalDateTime closesAt = StoreClock.closingAt(store, now);
        LocalDateTime maxAt = now.plusMinutes(game.getMaxMinutes());
        StoreMatch match = storeMatchRepository.save(StoreMatch.builder()
            .storeId(storeId).storeRoomId(storeRoomId).boardGameId(game.getId()).partyId(party.getId())
            .recorderMemberId(memberId).businessDate(StoreClock.businessDate(store, now))
            .minMinutes(game.getMinMinutes()).maxMinutes(game.getMaxMinutes())
            .startedAt(now).saveDeadline(maxAt.isBefore(closesAt) ? maxAt : closesAt).closesAt(closesAt)
            .build());
        for (StorePartyMember pm : partyService.activeMembers(party.getId())) {
            participantRepository.save(new StoreMatchParticipant(match.getId(), pm.getMemberId()));
        }
        notifier.changed(party.getId());
        return view(match.getId(), memberId);
    }

    // ── 점수판 ──

    /** 기록자 입력을 같이 하는 사람 화면(3f)에 실시간으로 */
    @Transactional
    public int saveDraft(Long matchId, Long memberId, String draftJson) {
        StoreMatch match = lockMatch(matchId);
        requireRecorder(match, memberId);
        if (!match.isEditable()) throw new StoreException(StoreErrorCode.MATCH_STATE);
        if (draftJson != null && draftJson.length() > MAX_DRAFT_LENGTH) throw new StoreException(StoreErrorCode.INVALID_RESULT);
        match.updateDraft(draftJson);
        notifier.changed(match.getPartyId());
        return match.getDraftVersion();
    }

    @Transactional
    public StoreDto.MatchResponse submit(Long matchId, Long memberId, StoreDto.SubmitRequest request) {
        return submit(matchId, memberId, request, StorePartyService.now());
    }

    @Transactional
    StoreDto.MatchResponse submit(Long matchId, Long memberId, StoreDto.SubmitRequest request, LocalDateTime now) {
        StoreMatch match = lockMatch(matchId);
        requireRecorder(match, memberId);
        if (!match.isEditable()) throw new StoreException(StoreErrorCode.MATCH_STATE);
        if (now.isBefore(match.saveOpensAt())) throw new StoreException(StoreErrorCode.SAVE_TOO_EARLY);
        LocalDateTime deadline = match.getFirstSavedAt() == null ? match.getSaveDeadline() : match.getClosesAt();
        if (now.isAfter(deadline)) throw new StoreException(StoreErrorCode.SAVE_DEADLINE_PASSED);

        List<StoreMatchParticipant> participants = participantRepository.findByStoreMatchIdOrderByIdAsc(matchId);
        applyResults(participants, request);
        for (StoreMatchParticipant p : participants) {
            p.clearConfirm();
            if (memberId.equals(p.getMemberId())) p.confirm(now);
        }
        match.submit(now, now.plus(CONFIRM_WINDOW));
        if (allConfirmed(participants)) recordMatch(match, participants, EndReason.CONFIRMED, now);
        notifier.changed(match.getPartyId());
        return view(matchId, memberId);
    }

    @Transactional
    public StoreDto.MatchResponse confirm(Long matchId, Long memberId) {
        LocalDateTime now = StorePartyService.now();
        StoreMatch match = lockMatch(matchId);
        if (match.getStatus() != StoreMatch.Status.AWAITING_CONFIRM) throw new StoreException(StoreErrorCode.MATCH_STATE);
        List<StoreMatchParticipant> participants = participantRepository.findByStoreMatchIdOrderByIdAsc(matchId);
        participant(participants, memberId).confirm(now);
        if (allConfirmed(participants)) recordMatch(match, participants, EndReason.CONFIRMED, now);
        notifier.changed(match.getPartyId());
        return view(matchId, memberId);
    }

    /** 3j "점수가 달라요" → 기록자 3k */
    @Transactional
    public StoreDto.MatchResponse dispute(Long matchId, Long memberId) {
        StoreMatch match = lockMatch(matchId);
        if (match.getStatus() != StoreMatch.Status.AWAITING_CONFIRM) throw new StoreException(StoreErrorCode.MATCH_STATE);
        List<StoreMatchParticipant> participants = participantRepository.findByStoreMatchIdOrderByIdAsc(matchId);
        participant(participants, memberId);
        participants.forEach(StoreMatchParticipant::clearConfirm);
        match.dispute(memberId);
        notifier.changed(match.getPartyId());
        return view(matchId, memberId);
    }

    /** 3i "점수 고치기" — 받은 확인이 모두 취소된다 */
    @Transactional
    public StoreDto.MatchResponse reopen(Long matchId, Long memberId) {
        StoreMatch match = lockMatch(matchId);
        requireRecorder(match, memberId);
        if (match.getStatus() != StoreMatch.Status.AWAITING_CONFIRM) throw new StoreException(StoreErrorCode.MATCH_STATE);
        participantRepository.findByStoreMatchIdOrderByIdAsc(matchId).forEach(StoreMatchParticipant::clearConfirm);
        match.reopen();
        notifier.changed(match.getPartyId());
        return view(matchId, memberId);
    }

    /** 게임 취소 (3d·3k 오른쪽 위). 이 판은 기록되지 않고 그룹은 그대로 */
    @Transactional
    public StoreDto.MatchResponse cancel(Long matchId, Long memberId) {
        StoreMatch match = lockMatch(matchId);
        requireRecorder(match, memberId);
        if (!match.isEditable()) throw new StoreException(StoreErrorCode.MATCH_STATE);
        match.cancel(StorePartyService.now());
        notifier.changed(match.getPartyId());
        return view(matchId, memberId);
    }

    // ── 자동 종료 (스케줄러) ──

    /** 저장 마감·영업 마감·5분 확인이 지난 판을 닫는다. 바뀌었으면 true */
    @Transactional
    public boolean closeIfDue(Long matchId, LocalDateTime now) {
        StoreMatch match = lockMatch(matchId);
        if (!match.isOpen()) return false;
        if (match.getStatus() == StoreMatch.Status.AWAITING_CONFIRM) {
            if (match.getConfirmDeadline() == null || now.isBefore(match.getConfirmDeadline())) return false;
            recordMatch(match, participantRepository.findByStoreMatchIdOrderByIdAsc(matchId), EndReason.TIMEOUT, now);
        } else if (match.getFirstSavedAt() == null) {
            if (!now.isAfter(match.getSaveDeadline())) return false;
            boolean byClosing = !match.getSaveDeadline().isBefore(match.getClosesAt());
            match.expire(byClosing ? EndReason.EXPIRED_CLOSE : EndReason.EXPIRED_MAX, now);
        } else {
            if (!now.isAfter(match.getClosesAt())) return false;
            match.expire(EndReason.EXPIRED_CLOSE, now);
        }
        notifier.changed(match.getPartyId());
        return true;
    }

    public List<Long> openMatchIds() {
        return storeMatchRepository.findByStatusIn(StorePartyService.OPEN).stream().map(StoreMatch::getId).toList();
    }

    // ── 기록 ──

    private void recordMatch(StoreMatch match, List<StoreMatchParticipant> participants, EndReason reason, LocalDateTime now) {
        List<StoreMatchParticipant> rated = participants.stream()
            .filter(p -> p.getMemberId() != null && p.getPlacement() != null)
            .toList();
        List<StoreRating> ratings = new ArrayList<>();
        List<StoreEloCalculator.Player> players = new ArrayList<>();
        for (StoreMatchParticipant p : rated) {
            StoreRating r = storeRatingRepository
                .findByStoreIdAndBoardGameIdAndMemberId(match.getStoreId(), match.getBoardGameId(), p.getMemberId())
                .orElseGet(() -> new StoreRating(match.getStoreId(), match.getBoardGameId(), p.getMemberId()));
            ratings.add(r);
            players.add(new StoreEloCalculator.Player(r.getRating(), r.getGames(), p.getPlacement()));
        }
        double[] updated = eloCalculator.calculate(players);
        for (int i = 0; i < rated.size(); i++) {
            StoreMatchParticipant p = rated.get(i);
            StoreRating r = ratings.get(i);
            double before = r.getRating();
            r.apply(updated[i], p.getPlacement() == 1, now);
            storeRatingRepository.save(r);
            p.applyRating(before, updated[i]);
            // 기록되면 그때부터 다시 3시간 (plan-store §1 매장 체크인)
            storeMemberRepository.findByStoreIdAndMemberId(match.getStoreId(), p.getMemberId())
                .ifPresent(sm -> sm.recorded(now));
        }
        match.record(reason, now);
    }

    static void applyResults(List<StoreMatchParticipant> participants, StoreDto.SubmitRequest request) {
        List<StoreMatchParticipant> active = participants.stream().filter(p -> p.getMemberId() != null).toList();
        if (request == null || request.participants() == null || request.participants().size() != active.size()) {
            throw new StoreException(StoreErrorCode.INVALID_RESULT);
        }
        Map<Long, StoreDto.SubmitParticipant> byMember = new HashMap<>();
        for (StoreDto.SubmitParticipant sp : request.participants()) {
            if (sp.memberId() == null || sp.placement() == null || sp.placement() < 1 || sp.placement() > active.size()
                || byMember.put(sp.memberId(), sp) != null) {
                throw new StoreException(StoreErrorCode.INVALID_RESULT);
            }
        }
        Set<Long> expected = new HashSet<>();
        active.forEach(p -> expected.add(p.getMemberId()));
        if (!expected.equals(byMember.keySet())) throw new StoreException(StoreErrorCode.INVALID_RESULT);
        if (byMember.values().stream().noneMatch(sp -> sp.placement() == 1)) throw new StoreException(StoreErrorCode.INVALID_RESULT);
        for (StoreMatchParticipant p : active) {
            StoreDto.SubmitParticipant sp = byMember.get(p.getMemberId());
            String json = sp.scoresJson();
            if (json != null && json.length() > MAX_DRAFT_LENGTH) throw new StoreException(StoreErrorCode.INVALID_RESULT);
            p.setResult(sp.placement(), sp.gameScore(), json);
        }
    }

    private static boolean allConfirmed(List<StoreMatchParticipant> participants) {
        return participants.stream().filter(p -> p.getMemberId() != null).allMatch(p -> p.getConfirmedAt() != null);
    }

    // ── 조회 ──

    public StoreDto.MatchResponse view(Long matchId, Long memberId) {
        StoreMatch match = storeMatchRepository.findById(matchId)
            .orElseThrow(() -> new StoreException(StoreErrorCode.MATCH_NOT_FOUND));
        List<StoreMatchParticipant> participants = participantRepository.findByStoreMatchIdOrderByIdAsc(matchId);
        boolean inMatch = participants.stream().anyMatch(p -> memberId.equals(p.getMemberId()));
        boolean inParty = partyService.membership(memberId).map(pm -> pm.getPartyId().equals(match.getPartyId())).orElse(false);
        if (!inMatch && !inParty) throw new StoreException(StoreErrorCode.NOT_PARTICIPANT);

        Store store = storeRepository.findById(match.getStoreId()).orElseThrow();
        String gameName = boardGameRepository.findById(match.getBoardGameId()).map(BoardGame::getName).orElse(null);
        List<Long> ids = new ArrayList<>(participants.stream().map(StoreMatchParticipant::getMemberId).filter(Objects::nonNull).toList());
        if (match.getRecorderMemberId() != null) ids.add(match.getRecorderMemberId());
        if (match.getDisputedByMemberId() != null) ids.add(match.getDisputedByMemberId());
        Map<Long, String> names = partyService.nicknames(ids);

        boolean recorded = match.getStatus() == StoreMatch.Status.RECORDED;
        List<StoreDto.MatchParticipantItem> items = participants.stream()
            .map(p -> participantItem(p, names, recorded))
            .toList();

        Integer rankBefore = null;
        Integer rankAfter = null;
        StoreMatchParticipant mine = participants.stream().filter(p -> memberId.equals(p.getMemberId())).findFirst().orElse(null);
        if (recorded && mine != null && mine.getRatingAfter() != null) {
            Integer[] ranks = rankChange(match, participants, memberId);
            rankBefore = ranks[0];
            rankAfter = ranks[1];
        }
        return new StoreDto.MatchResponse(match.getId(),
            new StoreDto.StoreBrief(store.getId(), store.getName(), store.getBranch()),
            match.getStoreRoomId(), match.getBoardGameId(), gameName, match.getStatus().name(),
            match.getEndReason() == null ? null : match.getEndReason().name(), match.getPartyId(),
            match.getRecorderMemberId(), nameOf(names, match.getRecorderMemberId()), match.getMinMinutes(),
            match.getMaxMinutes(), StoreDto.iso(match.getStartedAt()), StoreDto.iso(match.saveOpensAt()),
            StoreDto.iso(match.getSaveDeadline()), StoreDto.iso(match.getClosesAt()),
            StoreDto.iso(match.getFirstSavedAt()), StoreDto.iso(match.getConfirmDeadline()),
            StoreDto.iso(match.getEndedAt()), StoreDto.iso(StorePartyService.now()),
            match.getDisputedByMemberId(), nameOf(names, match.getDisputedByMemberId()), match.getDraftJson(), match.getDraftVersion(), items,
            rankBefore, rankAfter);
    }

    private static String nameOf(Map<Long, String> names, Long memberId) {
        return memberId == null ? null : names.get(memberId);
    }

    static StoreDto.MatchParticipantItem participantItem(StoreMatchParticipant p, Map<Long, String> names, boolean recorded) {
        Integer before = recorded && p.getRatingBefore() != null ? (int) Math.round(p.getRatingBefore()) : null;
        Integer after = recorded && p.getRatingAfter() != null ? (int) Math.round(p.getRatingAfter()) : null;
        return new StoreDto.MatchParticipantItem(p.getMemberId(),
            nameOf(names, p.getMemberId()), p.getPlacement(), p.getGameScore(),
            p.getScoresJson(), p.getConfirmedAt() != null, before, after,
            before != null && after != null ? after - before : null);
    }

    /** 3n "이번 시즌 순위 4위 → 3위". 이 판 참가자 점수만 판 전 값으로 바꿔 순위를 다시 센다 */
    private Integer[] rankChange(StoreMatch match, List<StoreMatchParticipant> participants, Long memberId) {
        Map<Long, Double> after = new HashMap<>();
        for (StoreRating r : storeRatingRepository.findByStoreIdAndBoardGameId(match.getStoreId(), match.getBoardGameId())) {
            after.put(r.getMemberId(), r.getRating());
        }
        Map<Long, Double> before = new HashMap<>(after);
        for (StoreMatchParticipant p : participants) {
            if (p.getMemberId() == null || p.getRatingBefore() == null) continue;
            before.put(p.getMemberId(), p.getRatingBefore());
            after.put(p.getMemberId(), p.getRatingAfter());
        }
        // 판 전에 이 게임 기록이 없던 사람은 판 전 순위에 넣지 않는다
        for (StoreMatchParticipant p : participants) {
            if (p.getMemberId() == null) continue;
            int gamesNow = storeRatingRepository.findByStoreIdAndBoardGameIdAndMemberId(
                match.getStoreId(), match.getBoardGameId(), p.getMemberId()).map(StoreRating::getGames).orElse(0);
            if (gamesNow <= 1) before.remove(p.getMemberId());
        }
        return new Integer[] { rankOf(before, memberId), rankOf(after, memberId) };
    }

    private static Integer rankOf(Map<Long, Double> ratings, Long memberId) {
        Double mine = ratings.get(memberId);
        if (mine == null) return null;
        return 1 + (int) ratings.values().stream().filter(v -> v > mine).count();
    }

    // ── 공통 ──

    private StoreParty storeParty(Long partyId) {
        return partyService.findParty(partyId).orElse(null);
    }

    /** 그룹 행을 먼저 잠그고 판을 읽는다 — 판 엔티티를 잠금 전에 올리면 옛 상태를 본다 */
    private StoreMatch lockMatch(Long matchId) {
        Long partyId = storeMatchRepository.findPartyIdById(matchId)
            .orElseThrow(() -> new StoreException(StoreErrorCode.MATCH_NOT_FOUND));
        partyService.lock(partyId);
        return storeMatchRepository.findById(matchId).orElseThrow();
    }

    private static void requireRecorder(StoreMatch match, Long memberId) {
        if (!memberId.equals(match.getRecorderMemberId())) throw new StoreException(StoreErrorCode.NOT_RECORDER);
    }

    private static StoreMatchParticipant participant(List<StoreMatchParticipant> participants, Long memberId) {
        return participants.stream().filter(p -> memberId.equals(p.getMemberId())).findFirst()
            .orElseThrow(() -> new StoreException(StoreErrorCode.NOT_PARTICIPANT));
    }

    static StoreErrorCode blockError(String block) {
        return switch (block) {
            case "STORE_CLOSED" -> StoreErrorCode.STORE_CLOSED;
            case "STAFF" -> StoreErrorCode.STAFF_CANNOT_PLAY;
            case "NO_PARTY" -> StoreErrorCode.NOT_IN_PARTY;
            case "NOT_RECORDER" -> StoreErrorCode.NOT_RECORDER;
            case "ROOM_CLOSED" -> StoreErrorCode.ROOM_CLOSED;
            case "GAME_TIME_NOT_SET" -> StoreErrorCode.GAME_TIME_NOT_SET;
            case "PARTY_PLAYING" -> StoreErrorCode.PARTY_PLAYING;
            case "PLAYER_COUNT" -> StoreErrorCode.PLAYER_COUNT;
            case "STORE_NOT_OPEN" -> StoreErrorCode.STORE_NOT_OPEN;
            case "TOO_CLOSE_TO_CLOSING" -> StoreErrorCode.TOO_CLOSE_TO_CLOSING;
            case "NOT_CHECKED_IN" -> StoreErrorCode.NOT_CHECKED_IN;
            default -> StoreErrorCode.MEMBER_NOT_CHECKED_IN;
        };
    }
}
