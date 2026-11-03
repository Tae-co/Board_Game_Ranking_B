package com.board_game_back.Service;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreContest;
import com.board_game_back.Entity.StoreContestEntry;
import com.board_game_back.Entity.StoreMatch;
import com.board_game_back.Entity.StoreMatchParticipant;
import com.board_game_back.Entity.StoreParty;
import com.board_game_back.Entity.StorePartyMember.LeftReason;
import com.board_game_back.Entity.StoreRoom;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.StoreContestEntryRepository;
import com.board_game_back.Repository.StoreContestRepository;
import com.board_game_back.Repository.StoreMatchParticipantRepository;
import com.board_game_back.Repository.StoreMatchRepository;
import com.board_game_back.Repository.StoreMemberRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Repository.StoreRoomRepository;
import com.board_game_back.Service.StoreAccessService.ViewerRole;
import com.board_game_back.Utils.StoreClock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하루 대회 (plan-store §1 이벤트·대회 점수·대회 시작, 5a~5e).
 *
 * <ul>
 *   <li>신청: 매장 멤버, 정원까지. 매장 밖에서도 (체크인 없이)
 *   <li>당일 STAFF가 도착 확인(= 그날 체크인) → 게임 최소 인원이 모이면 시작. 안 온 사람은 빠짐
 *   <li>시작하면 도착한 사람이 대회 그룹으로 묶이고(원래 그룹에선 잠시 빠짐) 1판이 바로 열린다
 *   <li>매판: 같이 한 사람 확인(3j) → STAFF 확인(5c) → 다음 판이 같은 기록자로 열림. 마지막 판이면 끝
 *   <li>순위 점수 = 그 판 인원 - 순위 + 1 의 합, 같으면 게임 점수 합, 그것도 같으면 공동 순위
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class StoreContestService {

    static final Set<StoreContestEntry.Status> ACTIVE_ENTRY =
        EnumSet.of(StoreContestEntry.Status.APPLIED, StoreContestEntry.Status.PLAYING);
    /** 대회 판엔 저장 마감이 없다. 컬럼이 NOT NULL이라 넉넉히 넣어 둔다 (스케줄러는 대회 판을 마감시키지 않음) */
    private static final Duration NO_LIMIT = Duration.ofDays(2);
    /** 4g: 끝난 뒤 1시간 동안 시상대 */
    static final Duration BOARD_AFTER_END = Duration.ofHours(1);
    private static final int MAX_ROUNDS = 10;

    private final StoreRepository storeRepository;
    private final StoreRoomRepository storeRoomRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreContestRepository contestRepository;
    private final StoreContestEntryRepository entryRepository;
    private final StoreMatchRepository matchRepository;
    private final StoreMatchParticipantRepository participantRepository;
    private final BoardGameRepository boardGameRepository;
    private final StoreAccessService accessService;
    private final StorePartyService partyService;
    private final StoreMatchService matchService;
    private final StorePartyNotifier notifier;

    // ── 5a·5b·5d 보기 ──

    public StoreDto.ContestResponse view(Long storeId, Long contestId, Long memberId) {
        LocalDateTime now = StorePartyService.now();
        StoreContest contest = getContest(storeId, contestId);
        Store store = storeRepository.findById(storeId).orElseThrow();
        BoardGame game = boardGameRepository.findById(contest.getBoardGameId()).orElseThrow();
        boolean roomClosed = storeRoomRepository.findById(contest.getStoreRoomId()).map(StoreRoom::isClosed).orElse(true);
        ViewerRole viewer = accessService.viewerRole(storeId, memberId);

        List<StoreContestEntry> entries = entryRepository.findByContestIdOrderByAppliedAtAscIdAsc(contestId);
        List<StoreMatch> matches = matchRepository.findByContestIdOrderByIdAsc(contestId);
        Map<Long, List<StoreMatchParticipant>> byMatch = participantsByMatch(matches);
        List<Long> ids = new ArrayList<>(entries.stream().map(StoreContestEntry::getMemberId).filter(Objects::nonNull).toList());
        if (contest.getRecorderMemberId() != null) ids.add(contest.getRecorderMemberId());
        Map<Long, String> names = partyService.nicknames(ids);

        StoreContestEntry mine = entries.stream().filter(e -> memberId.equals(e.getMemberId())).findFirst().orElse(null);
        List<StoreDto.ContestEntryItem> entryItems = entries.stream()
            .map(e -> new StoreDto.ContestEntryItem(e.getMemberId(), names.get(e.getMemberId()), e.isArrived(),
                e.getStatus().name(), memberId.equals(e.getMemberId())))
            .toList();
        StoreMatch open = matches.stream().filter(StoreMatch::isOpen).reduce((a, b) -> b).orElse(null);

        return new StoreDto.ContestResponse(contest.getId(),
            new StoreDto.StoreBrief(store.getId(), store.getName(), store.getBranch()),
            contest.getTitle(), contest.getStoreRoomId(), contest.getBoardGameId(), game.getName(),
            game.getMinPlayers(), game.getMaxPlayers(), StoreDto.iso(contest.getStartsAt()),
            isToday(store, contest, now), contest.getCapacity(), contest.getRounds(), contest.getPrizeText(),
            contest.getStatus().name(), contest.getEndReason() == null ? null : contest.getEndReason().name(),
            roomClosed, viewer.name(), mine == null ? null : mine.getStatus().name(), mine != null && mine.isArrived(),
            entryItems, contest.getRecorderMemberId(),
            contest.getRecorderMemberId() == null ? null : names.get(contest.getRecorderMemberId()),
            roundStates(contest, matches, byMatch, memberId), standings(contest, entries, matches, byMatch, names, memberId),
            open == null ? null : open.getId(), StoreDto.iso(now));
    }

    // ── 5a 신청 ──

    @Transactional
    public StoreDto.ContestResponse apply(Long storeId, Long contestId, Long memberId) {
        LocalDateTime now = StorePartyService.now();
        Store store = getOpenStore(storeId);
        if (accessService.isStaff(memberId)) throw new StoreException(StoreErrorCode.STAFF_CANNOT_PLAY);
        storeMemberRepository.findByStoreIdAndMemberId(storeId, memberId)
            .orElseThrow(() -> new StoreException(StoreErrorCode.NOT_MEMBER));
        StoreContest contest = lockContest(storeId, contestId);
        if (!contest.isRecruiting() || isDayOver(store, contest, now)) throw new StoreException(StoreErrorCode.CONTEST_STATE);
        if (entryRepository.findByContestIdAndMemberId(contestId, memberId).isEmpty()) {
            long applied = entryRepository.findByContestIdOrderByAppliedAtAscIdAsc(contestId).size();
            if (applied >= contest.getCapacity()) throw new StoreException(StoreErrorCode.CONTEST_FULL);
            entryRepository.save(new StoreContestEntry(contestId, memberId, now));
        }
        return view(storeId, contestId, memberId);
    }

    @Transactional
    public StoreDto.ContestResponse withdraw(Long storeId, Long contestId, Long memberId) {
        StoreContest contest = lockContest(storeId, contestId);
        if (!contest.isRecruiting()) throw new StoreException(StoreErrorCode.CONTEST_STATE);
        entryRepository.findByContestIdAndMemberId(contestId, memberId).ifPresent(entryRepository::delete);
        return view(storeId, contestId, memberId);
    }

    // ── 5e STAFF ──

    /** 도착 확인 = 그날 체크인 (QR 안 찍어도 됨) */
    @Transactional
    public StoreDto.ContestResponse setArrived(Long storeId, Long contestId, Long staffId, Long targetId, boolean arrived) {
        LocalDateTime now = StorePartyService.now();
        Store store = getOpenStore(storeId);
        accessService.requireStaffOf(storeId, staffId);
        StoreContest contest = lockContest(storeId, contestId);
        if (!contest.isRecruiting()) throw new StoreException(StoreErrorCode.CONTEST_STATE);
        if (!isToday(store, contest, now)) throw new StoreException(StoreErrorCode.CONTEST_NOT_TODAY);
        StoreContestEntry entry = entryRepository.findByContestIdAndMemberId(contestId, targetId)
            .orElseThrow(() -> new StoreException(StoreErrorCode.NOT_PARTICIPANT));
        entry.setArrived(arrived, now);
        if (arrived) storeMemberRepository.findByStoreIdAndMemberId(storeId, targetId).ifPresent(sm -> sm.checkIn(now));
        return view(storeId, contestId, staffId);
    }

    @Transactional
    public StoreDto.ContestResponse start(Long storeId, Long contestId, Long staffId, Long recorderId) {
        LocalDateTime now = StorePartyService.now();
        Store store = getOpenStore(storeId);
        accessService.requireStaffOf(storeId, staffId);
        StoreContest contest = lockContest(storeId, contestId);
        if (!contest.isRecruiting()) throw new StoreException(StoreErrorCode.CONTEST_STATE);
        if (!isToday(store, contest, now)) throw new StoreException(StoreErrorCode.CONTEST_NOT_TODAY);
        StoreRoom room = storeRoomRepository.findById(contest.getStoreRoomId()).orElseThrow();
        // 잠시 닫힌 게임은 다시 열기 전엔 시작할 수 없다 (45차)
        if (room.isClosed()) throw new StoreException(StoreErrorCode.ROOM_CLOSED);
        BoardGame game = boardGameRepository.findById(contest.getBoardGameId()).orElseThrow();

        List<StoreContestEntry> entries = entryRepository.findByContestIdOrderByAppliedAtAscIdAsc(contestId);
        // 다른 대회를 하고 있는 사람은 이 대회에 들어갈 수 없다
        List<StoreContestEntry> arrived = entries.stream()
            .filter(e -> e.isArrived() && e.getMemberId() != null && !inContestParty(e.getMemberId()))
            .toList();
        int max = Math.min(game.getMaxPlayers(), contest.getCapacity());
        if (arrived.size() < game.getMinPlayers() || arrived.size() > max) throw new StoreException(StoreErrorCode.PLAYER_COUNT);

        Long recorder = arrived.stream().map(StoreContestEntry::getMemberId)
            .filter(id -> id.equals(recorderId)).findFirst()
            .orElse(arrived.get(0).getMemberId());
        for (StoreContestEntry e : entries) {
            if (arrived.contains(e)) e.play(partyService.leaveForContest(e.getMemberId(), now));
            else e.exclude();
        }
        // 기록자가 맨 앞이어야 그룹 기록자 승계 순서(가장 먼저 합류)가 신청 순서를 따른다
        List<Long> memberIds = arrived.stream().map(StoreContestEntry::getMemberId).toList();
        StoreParty party = partyService.createContestParty(storeId, contestId, recorder, memberIds,
            StoreClock.businessDate(store, now), now);
        contest.start(party.getId(), recorder, now);
        openRound(contest, party, 1, now);
        notifier.changed(party.getId());
        return view(storeId, contestId, staffId);
    }

    /** 시작 전에만 (5e "대회 취소") */
    @Transactional
    public StoreDto.ContestResponse cancel(Long storeId, Long contestId, Long staffId) {
        accessService.requireStaffOf(storeId, staffId);
        StoreContest contest = lockContest(storeId, contestId);
        if (!contest.isRecruiting()) throw new StoreException(StoreErrorCode.CONTEST_STATE);
        contest.cancel(StoreContest.EndReason.STAFF_CANCEL, StorePartyService.now());
        return view(storeId, contestId, staffId);
    }

    /** 5c "확인하고 반영" → 다음 판이 같은 기록자로 바로 열림. 마지막 판이면 대회 끝 */
    @Transactional
    public StoreDto.ContestResponse approve(Long storeId, Long contestId, Long matchId, Long staffId) {
        LocalDateTime now = StorePartyService.now();
        accessService.requireStaffOf(storeId, staffId);
        StoreContest contest = lockContest(storeId, contestId);
        if (!contest.isPlaying()) throw new StoreException(StoreErrorCode.CONTEST_STATE);
        StoreMatch match = matchService.lockMatch(matchId);
        if (!contestId.equals(match.getContestId()) || match.getStatus() != StoreMatch.Status.AWAITING_STAFF) {
            throw new StoreException(StoreErrorCode.MATCH_STATE);
        }
        match.record(StoreMatch.EndReason.CONFIRMED, now);
        // 대회 판도 체크인 갱신·방문·참여엔 들어간다 (매장 Elo만 빠짐)
        for (StoreMatchParticipant p : participantRepository.findByStoreMatchIdOrderByIdAsc(matchId)) {
            if (p.getMemberId() == null) continue;
            storeMemberRepository.findByStoreIdAndMemberId(storeId, p.getMemberId()).ifPresent(sm -> sm.recorded(now));
        }
        if (match.getContestRound() >= contest.getRounds()) {
            finish(contest, StoreContest.EndReason.COMPLETED, now);
        } else {
            openRound(contest, partyService.lock(contest.getPartyId()), match.getContestRound() + 1, now);
        }
        notifier.changed(contest.getPartyId());
        return view(storeId, contestId, staffId);
    }

    /** 5c "점수가 달라요 · 기록자에게 돌려보내기" → 기록자 3k */
    @Transactional
    public StoreDto.ContestResponse sendBack(Long storeId, Long contestId, Long matchId, Long staffId) {
        accessService.requireStaffOf(storeId, staffId);
        StoreContest contest = lockContest(storeId, contestId);
        if (!contest.isPlaying()) throw new StoreException(StoreErrorCode.CONTEST_STATE);
        StoreMatch match = matchService.lockMatch(matchId);
        if (!contestId.equals(match.getContestId()) || match.getStatus() != StoreMatch.Status.AWAITING_STAFF) {
            throw new StoreException(StoreErrorCode.MATCH_STATE);
        }
        participantRepository.findByStoreMatchIdOrderByIdAsc(matchId).forEach(StoreMatchParticipant::clearConfirm);
        match.dispute(staffId);
        notifier.changed(match.getPartyId());
        return view(storeId, contestId, staffId);
    }

    // ── 나가기 · 탈퇴 · 운영 끝냄 · 스케줄러 ──

    /** 매장 나가기(1c): 신청한 대회는 취소, 진행 중 대회에선 빠진다. 대회 판이 진행 중이면 막는다 (판 사이에만) */
    @Transactional
    public void onLeaveStore(Long storeId, Long memberId) {
        leaveContests(memberId, storeId, false, LeftReason.LEAVE);
    }

    /** 회원 탈퇴: 판이 진행 중이어도 빠진다. 지금까지 판 기록은 '나간 멤버'로 남는다 */
    @Transactional
    public void onMemberDeleted(Long memberId) {
        leaveContests(memberId, null, true, LeftReason.WITHDRAW);
        entryRepository.anonymizeMember(memberId);
        contestRepository.anonymizeRecorder(memberId);
    }

    private void leaveContests(Long memberId, Long storeIdOrNull, boolean force, LeftReason reason) {
        LocalDateTime now = StorePartyService.now();
        for (StoreContestEntry entry : entryRepository.findByMemberIdAndStatusIn(memberId, ACTIVE_ENTRY)) {
            StoreContest contest = contestRepository.findByIdForUpdate(entry.getContestId()).orElse(null);
            if (contest == null || (storeIdOrNull != null && !contest.getStoreId().equals(storeIdOrNull))) continue;
            if (entry.getStatus() == StoreContestEntry.Status.APPLIED) {
                if (contest.isRecruiting()) entryRepository.delete(entry);
                continue;
            }
            if (contest.isPlaying()) removeFromContest(contest, entry, force, reason, now);
        }
    }

    private void removeFromContest(StoreContest contest, StoreContestEntry entry, boolean force, LeftReason reason,
                                   LocalDateTime now) {
        Long memberId = entry.getMemberId();
        StoreParty party = partyService.lock(contest.getPartyId());
        StoreMatch open = partyService.openMatch(party.getId()).orElse(null);
        // "판 사이" = STAFF가 앞 판을 확인해서 다음 판이 막 열렸고 아직 저장 전
        boolean between = open == null || (open.getStatus() == StoreMatch.Status.PLAYING && open.getFirstSavedAt() == null);
        if (!between && !force) throw new StoreException(StoreErrorCode.MATCH_OPEN);
        if (open != null && open.getFirstSavedAt() == null) {
            participantRepository.findByStoreMatchIdOrderByIdAsc(open.getId()).stream()
                .filter(p -> memberId.equals(p.getMemberId()))
                .forEach(participantRepository::delete);
        }
        entry.leave(now);
        // 매장 나가기는 그 매장 판 기록을 '나간 멤버'로 바꾸므로 대회 줄도 같이 (탈퇴는 onMemberDeleted가 일괄로)
        if (reason == LeftReason.LEAVE) entry.anonymize();
        partyService.leaveContestParty(party.getId(), memberId, reason, now);
        if (memberId.equals(contest.getRecorderMemberId())) {
            contest.changeRecorder(party.isActive() ? party.getRecorderMemberId() : null);
            if (open != null && open.getFirstSavedAt() == null) open.changeRecorder(contest.getRecorderMemberId());
        }
        BoardGame game = boardGameRepository.findById(contest.getBoardGameId()).orElseThrow();
        long remaining = entryRepository.findByContestIdOrderByAppliedAtAscIdAsc(contest.getId()).stream()
            .filter(e -> e.getStatus() == StoreContestEntry.Status.PLAYING)
            .count();
        // 최소 인원 밑이면 그 시점 점수로 끝 (5e)
        if (remaining < game.getMinPlayers()) finish(contest, StoreContest.EndReason.SHORT_HANDED, now);
        notifier.changed(party.getId());
    }

    /** 운영 끝내기(6f): 예정된 대회는 취소, 진행 중 대회는 그 시점 점수로 끝 */
    @Transactional
    public void onStoreClosed(Long storeId) {
        LocalDateTime now = StorePartyService.now();
        for (StoreContest c : contestRepository.findByStoreIdAndStatusIn(storeId,
                EnumSet.of(StoreContest.Status.RECRUITING, StoreContest.Status.PLAYING))) {
            StoreContest contest = contestRepository.findByIdForUpdate(c.getId()).orElseThrow();
            if (contest.isRecruiting()) contest.cancel(StoreContest.EndReason.STORE_CLOSED, now);
            else if (contest.isPlaying()) finish(contest, StoreContest.EndReason.STORE_CLOSED, now);
        }
    }

    /** 하루 대회: 그날 영업일이 끝날 때까지 시작 안 했으면 취소 (48차) */
    @Transactional
    public boolean cancelIfDayOver(Long contestId, LocalDateTime now) {
        StoreContest contest = contestRepository.findByIdForUpdate(contestId).orElse(null);
        if (contest == null || !contest.isRecruiting()) return false;
        Store store = storeRepository.findById(contest.getStoreId()).orElseThrow();
        if (!isDayOver(store, contest, now)) return false;
        contest.cancel(StoreContest.EndReason.DAY_OVER, now);
        return true;
    }

    public List<Long> recruitingIds() {
        return contestRepository.findByStatus(StoreContest.Status.RECRUITING).stream().map(StoreContest::getId).toList();
    }

    // ── 매장 홈 카드 · TV (2·4d·4g) ──

    /** 모집 중(오늘 이후)·진행 중, 그리고 오늘 끝나거나 취소된 대회 (결과·취소 안내를 볼 수 있게) */
    public List<StoreDto.ContestCard> cards(Store store, Long memberIdOrNull) {
        LocalDateTime now = StorePartyService.now();
        LocalDate today = StoreClock.businessDate(store, now);
        List<StoreContest> contests = contestRepository.findByStoreIdOrderByStartsAtDesc(store.getId()).stream()
            .filter(c -> switch (c.getStatus()) {
                case RECRUITING -> !c.getBusinessDate().isBefore(today);
                case PLAYING -> true;
                case FINISHED, CANCELLED -> c.getBusinessDate().equals(today);
            })
            // 진행 중 → 모집 중 → 오늘 끝남·취소 순, 같은 칸 안에선 시작 시각 순
            .sorted(Comparator.comparingInt((StoreContest c) -> switch (c.getStatus()) {
                case PLAYING -> 0;
                case RECRUITING -> 1;
                default -> 2;
            }).thenComparing(StoreContest::getStartsAt))
            .toList();
        return cards(store, contests, memberIdOrNull, today);
    }

    /** TV 4d: 모집 중인 대회만 */
    public List<StoreDto.ContestCard> recruitingCards(Store store) {
        return cards(store, null).stream()
            .filter(c -> c.status().equals(StoreContest.Status.RECRUITING.name()))
            .toList();
    }

    /** TV 4g: 진행 중이거나 끝난 지 1시간 안. 없으면 null (그 화면은 건너뜀) */
    public StoreDto.ContestBoard board(Store store, LocalDateTime now) {
        StoreContest contest = contestRepository.findByStoreIdAndStatusIn(store.getId(),
                EnumSet.of(StoreContest.Status.PLAYING, StoreContest.Status.FINISHED)).stream()
            .filter(c -> c.isPlaying() || (c.getEndedAt() != null && c.getEndedAt().isAfter(now.minus(BOARD_AFTER_END))))
            .max(Comparator.comparing((StoreContest c) -> c.isPlaying()).thenComparing(StoreContest::getStartsAt))
            .orElse(null);
        if (contest == null) return null;
        List<StoreContestEntry> entries = entryRepository.findByContestIdOrderByAppliedAtAscIdAsc(contest.getId());
        List<StoreMatch> matches = matchRepository.findByContestIdOrderByIdAsc(contest.getId());
        Map<Long, List<StoreMatchParticipant>> byMatch = participantsByMatch(matches);
        Map<Long, String> names = partyService.nicknames(entries.stream().map(StoreContestEntry::getMemberId).filter(Objects::nonNull).toList());
        String gameName = boardGameRepository.findById(contest.getBoardGameId()).map(BoardGame::getName).orElse(null);
        int done = (int) matches.stream().filter(m -> m.getStatus() == StoreMatch.Status.RECORDED).count();
        return new StoreDto.ContestBoard(contest.getId(), contest.getTitle(), gameName, contest.getRounds(), done,
            contest.getStatus().name(),
            standings(contest, entries, matches, byMatch, names, -1L).stream().filter(s -> !s.left()).toList());
    }

    /** 월간 리포트 "이번 달 이벤트 결과"용: 그 달 대회의 [대회, 참가 인원, 우승자] */
    public List<StoreDto.ReportEvent> reportContests(Store store, LocalDate from, LocalDate to) {
        List<StoreContest> contests = contestRepository.findByStoreIdAndBusinessDateBetweenOrderByStartsAtAsc(store.getId(), from, to);
        List<StoreDto.ReportEvent> result = new ArrayList<>();
        for (StoreContest c : contests) {
            List<StoreContestEntry> entries = entryRepository.findByContestIdOrderByAppliedAtAscIdAsc(c.getId());
            long players = entries.stream().filter(e -> e.getStatus() == StoreContestEntry.Status.PLAYING
                || e.getStatus() == StoreContestEntry.Status.LEFT).count();
            List<String> winners = List.of();
            if (c.getStatus() == StoreContest.Status.FINISHED) {
                List<StoreMatch> matches = matchRepository.findByContestIdOrderByIdAsc(c.getId());
                Map<Long, String> names = partyService.nicknames(entries.stream().map(StoreContestEntry::getMemberId).filter(Objects::nonNull).toList());
                winners = standings(c, entries, matches, participantsByMatch(matches), names, -1L).stream()
                    .filter(s -> Integer.valueOf(1).equals(s.rank()) && s.nickname() != null)
                    .map(StoreDto.ContestStanding::nickname)
                    .toList();
            }
            result.add(new StoreDto.ReportEvent("CONTEST", c.getTitle(), c.getBusinessDate().toString(),
                c.getStatus() == StoreContest.Status.CANCELLED ? entries.size() : players, winners, c.getStatus().name()));
        }
        return result;
    }

    /** 6i "예정된 대회 N개" */
    public long upcomingCount(Long storeRoomId) {
        return contestRepository.countByStoreRoomIdAndStatus(storeRoomId, StoreContest.Status.RECRUITING);
    }

    // ── 어드민 6j ──

    public List<StoreDto.AdminContestItem> adminList(Long storeId) {
        Store store = getStore(storeId);
        List<StoreContest> contests = contestRepository.findByStoreIdOrderByStartsAtDesc(storeId);
        Map<Long, Long> applied = entryRepository.findByContestIdIn(contests.stream().map(StoreContest::getId).toList()).stream()
            .collect(Collectors.groupingBy(StoreContestEntry::getContestId, Collectors.counting()));
        Map<Long, String> gameNames = gameNames(contests);
        Map<Long, Boolean> roomClosed = storeRoomRepository.findByStoreId(storeId).stream()
            .collect(Collectors.toMap(StoreRoom::getId, StoreRoom::isClosed));
        return contests.stream().map(c -> {
            var local = StoreClock.toLocal(store, c.getStartsAt());
            return new StoreDto.AdminContestItem(c.getId(), c.getTitle(), c.getBoardGameId(), gameNames.get(c.getBoardGameId()),
                local.toLocalDate().toString(), StoreDto.hhmm(local.toLocalTime()), c.getCapacity(), c.getRounds(),
                c.getPrizeText(), c.getStatus().name(), applied.getOrDefault(c.getId(), 0L),
                roomClosed.getOrDefault(c.getStoreRoomId(), true));
        }).toList();
    }

    @Transactional
    public List<StoreDto.AdminContestItem> create(Long storeId, StoreDto.ContestForm form) {
        Store store = getOpenStore(storeId);
        StoreContest contest = new StoreContest(storeId, StorePartyService.now());
        applyForm(store, contest, form, 0);
        contestRepository.save(contest);
        return adminList(storeId);
    }

    /** 신청자가 있어도 덮어쓴다 (35차). 모집 중에만 */
    @Transactional
    public List<StoreDto.AdminContestItem> update(Long storeId, Long contestId, StoreDto.ContestForm form) {
        Store store = getOpenStore(storeId);
        StoreContest contest = lockContest(storeId, contestId);
        if (!contest.isRecruiting()) throw new StoreException(StoreErrorCode.CONTEST_STATE);
        applyForm(store, contest, form, entryRepository.findByContestIdOrderByAppliedAtAscIdAsc(contestId).size());
        return adminList(storeId);
    }

    /** 대회 삭제 = 신청도 같이 사라짐. 진행 중·끝난 대회는 기록이 있어 지우지 않는다 */
    @Transactional
    public List<StoreDto.AdminContestItem> delete(Long storeId, Long contestId) {
        StoreContest contest = lockContest(storeId, contestId);
        if (contest.isPlaying() || contest.getStatus() == StoreContest.Status.FINISHED) {
            throw new StoreException(StoreErrorCode.CONTEST_STATE);
        }
        entryRepository.deleteByContestId(contestId);
        contestRepository.delete(contest);
        return adminList(storeId);
    }

    private void applyForm(Store store, StoreContest contest, StoreDto.ContestForm form, int applied) {
        if (form == null) throw new StoreException(StoreErrorCode.INVALID_CONTEST);
        String title = form.title() == null ? "" : form.title().trim();
        String prize = form.prizeText() == null || form.prizeText().isBlank() ? null : form.prizeText().trim();
        if (title.isEmpty() || title.length() > 40 || (prize != null && prize.length() > 80) || form.boardGameId() == null) {
            throw new StoreException(StoreErrorCode.INVALID_CONTEST);
        }
        // 잠시 닫힌 게임도 고를 수 있다 (대회 화면에 표시, 다시 열기 전엔 시작 불가)
        StoreRoom room = storeRoomRepository.findByStoreId(store.getId()).stream()
            .filter(r -> r.getBoardGameId().equals(form.boardGameId()))
            .findFirst()
            .orElseThrow(() -> new StoreException(StoreErrorCode.INVALID_CONTEST));
        BoardGame game = boardGameRepository.findById(form.boardGameId()).orElseThrow();
        int capacity = form.capacity() == null ? game.getMaxPlayers() : form.capacity();
        int rounds = form.rounds() == null ? 3 : form.rounds();
        if (capacity < game.getMinPlayers() || capacity > game.getMaxPlayers() || capacity < applied
            || rounds < 1 || rounds > MAX_ROUNDS) {
            throw new StoreException(StoreErrorCode.INVALID_CONTEST);
        }
        LocalDateTime startsAt;
        try {
            startsAt = LocalDate.parse(form.date()).atTime(java.time.LocalTime.parse(form.time()))
                .atZone(StoreClock.zone(store)).withZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime();
        } catch (RuntimeException e) {
            throw new StoreException(StoreErrorCode.INVALID_CONTEST);
        }
        LocalDate businessDate = StoreClock.businessDate(store, startsAt);
        if (businessDate.isBefore(StoreClock.businessDate(store, StorePartyService.now()))) {
            throw new StoreException(StoreErrorCode.INVALID_CONTEST);
        }
        contest.update(room.getId(), game.getId(), title, startsAt, businessDate, capacity, rounds, prize);
    }

    // ── 내부 ──

    private void openRound(StoreContest contest, StoreParty party, int round, LocalDateTime now) {
        Store store = storeRepository.findById(contest.getStoreId()).orElseThrow();
        StoreMatch match = matchRepository.save(StoreMatch.builder()
            .storeId(contest.getStoreId()).storeRoomId(contest.getStoreRoomId()).boardGameId(contest.getBoardGameId())
            .partyId(party.getId()).recorderMemberId(party.getRecorderMemberId())
            .businessDate(StoreClock.businessDate(store, now))
            .minMinutes(0).maxMinutes(0).startedAt(now)
            .saveDeadline(now.plus(NO_LIMIT)).closesAt(now.plus(NO_LIMIT))
            .contestId(contest.getId()).contestRound(round)
            .build());
        for (var pm : partyService.activeMembers(party.getId())) {
            participantRepository.save(new StoreMatchParticipant(match.getId(), pm.getMemberId()));
        }
    }

    private void finish(StoreContest contest, StoreContest.EndReason reason, LocalDateTime now) {
        StoreMatch open = partyService.openMatch(contest.getPartyId()).orElse(null);
        if (open != null) open.cancel(now);
        contest.finish(reason, now);
        Map<Long, Long> origins = new LinkedHashMap<>();
        for (StoreContestEntry e : entryRepository.findByContestIdOrderByAppliedAtAscIdAsc(contest.getId())) {
            if (e.getStatus() == StoreContestEntry.Status.PLAYING && e.getMemberId() != null) {
                origins.put(e.getMemberId(), e.getOriginPartyId());
            }
        }
        partyService.endContestParty(contest.getPartyId(), origins, now);
    }

    private List<StoreDto.ContestRound> roundStates(StoreContest contest, List<StoreMatch> matches,
                                                    Map<Long, List<StoreMatchParticipant>> byMatch, Long memberId) {
        Map<Integer, StoreMatch> byRound = new HashMap<>();
        for (StoreMatch m : matches) byRound.put(m.getContestRound(), m);
        List<StoreDto.ContestRound> result = new ArrayList<>();
        for (int r = 1; r <= contest.getRounds(); r++) {
            StoreMatch m = byRound.get(r);
            if (m == null) {
                result.add(new StoreDto.ContestRound(r, null, "WAITING", null, null));
                continue;
            }
            String status = m.getStatus() == StoreMatch.Status.RECORDED ? "DONE" : m.getStatus().name();
            Integer placement = null;
            Integer points = null;
            if (m.getStatus() == StoreMatch.Status.RECORDED) {
                List<StoreMatchParticipant> ps = byMatch.getOrDefault(m.getId(), List.of());
                StoreMatchParticipant mine = ps.stream().filter(p -> memberId.equals(p.getMemberId())).findFirst().orElse(null);
                if (mine != null && mine.getPlacement() != null) {
                    placement = mine.getPlacement();
                    points = points(ps.size(), mine.getPlacement());
                }
            }
            result.add(new StoreDto.ContestRound(r, m.getId(), status, placement, points));
        }
        return result;
    }

    /** 순위 점수 합 → 게임 점수 합. 둘 다 같으면 공동 순위. 대회 중 나간 사람은 순위 없이 맨 뒤 */
    static List<StoreDto.ContestStanding> standings(StoreContest contest, List<StoreContestEntry> entries,
                                                     List<StoreMatch> matches, Map<Long, List<StoreMatchParticipant>> byMatch,
                                                     Map<Long, String> names, Long memberId) {
        record Row(StoreContestEntry entry, Integer[] points, int total, double gameScore) {}
        List<Row> rows = new ArrayList<>();
        for (StoreContestEntry e : entries) {
            if (e.getStatus() != StoreContestEntry.Status.PLAYING && e.getStatus() != StoreContestEntry.Status.LEFT) continue;
            Integer[] pts = new Integer[contest.getRounds()];
            int total = 0;
            double score = 0;
            for (StoreMatch m : matches) {
                if (m.getStatus() != StoreMatch.Status.RECORDED || e.getMemberId() == null) continue;
                List<StoreMatchParticipant> ps = byMatch.getOrDefault(m.getId(), List.of());
                for (StoreMatchParticipant p : ps) {
                    if (!e.getMemberId().equals(p.getMemberId()) || p.getPlacement() == null) continue;
                    int pt = points(ps.size(), p.getPlacement());
                    if (m.getContestRound() >= 1 && m.getContestRound() <= pts.length) pts[m.getContestRound() - 1] = pt;
                    total += pt;
                    score += p.getGameScore() == null ? 0 : p.getGameScore();
                }
            }
            rows.add(new Row(e, pts, total, score));
        }
        Comparator<Row> order = Comparator.comparingInt(Row::total).reversed()
            .thenComparing(Comparator.comparingDouble(Row::gameScore).reversed());
        List<Row> ranked = rows.stream().filter(r -> r.entry().getStatus() == StoreContestEntry.Status.PLAYING).sorted(order).toList();
        List<StoreDto.ContestStanding> result = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Row r = ranked.get(i);
            int rank = i + 1;
            if (i > 0 && order.compare(ranked.get(i - 1), r) == 0) rank = result.get(i - 1).rank();
            result.add(standing(r.entry(), rank, r.points(), r.total(), r.gameScore(), names, memberId, false));
        }
        rows.stream().filter(r -> r.entry().getStatus() == StoreContestEntry.Status.LEFT)
            .forEach(r -> result.add(standing(r.entry(), null, r.points(), r.total(), r.gameScore(), names, memberId, true)));
        return result;
    }

    private static StoreDto.ContestStanding standing(StoreContestEntry e, Integer rank, Integer[] points, int total,
                                                     double gameScore, Map<Long, String> names, Long memberId, boolean left) {
        return new StoreDto.ContestStanding(rank, e.getMemberId(), e.getMemberId() == null ? null : names.get(e.getMemberId()),
            java.util.Arrays.asList(points), total, gameScore, memberId.equals(e.getMemberId()), left);
    }

    /** 그 판 인원 기준: 4인 1등 4 / 2등 3 …, 3인전 3/2/1 */
    static int points(int players, int placement) {
        return Math.max(0, players - placement + 1);
    }

    private List<StoreDto.ContestCard> cards(Store store, List<StoreContest> contests, Long memberIdOrNull, LocalDate today) {
        if (contests.isEmpty()) return List.of();
        List<StoreContestEntry> entries = entryRepository.findByContestIdIn(contests.stream().map(StoreContest::getId).toList());
        Map<Long, Long> applied = entries.stream().collect(Collectors.groupingBy(StoreContestEntry::getContestId, Collectors.counting()));
        Map<Long, StoreContestEntry> mine = memberIdOrNull == null ? Map.of() : entries.stream()
            .filter(e -> memberIdOrNull.equals(e.getMemberId()))
            .collect(Collectors.toMap(StoreContestEntry::getContestId, e -> e, (a, b) -> a));
        Map<Long, String> gameNames = gameNames(contests);
        Map<Long, Boolean> roomClosed = storeRoomRepository.findByStoreId(store.getId()).stream()
            .collect(Collectors.toMap(StoreRoom::getId, StoreRoom::isClosed));
        return contests.stream().map(c -> {
            StoreContestEntry me = mine.get(c.getId());
            return new StoreDto.ContestCard(c.getId(), c.getTitle(), c.getBoardGameId(), gameNames.get(c.getBoardGameId()),
                StoreDto.iso(c.getStartsAt()), c.getBusinessDate().equals(today), c.getCapacity(),
                applied.getOrDefault(c.getId(), 0L), c.getRounds(), c.getPrizeText(), c.getStatus().name(),
                roomClosed.getOrDefault(c.getStoreRoomId(), true), me == null ? null : me.getStatus().name(),
                me != null && me.isArrived());
        }).toList();
    }

    private Map<Long, String> gameNames(List<StoreContest> contests) {
        return boardGameRepository.findByIdIn(contests.stream().map(StoreContest::getBoardGameId).distinct().toList()).stream()
            .collect(Collectors.toMap(BoardGame::getId, BoardGame::getName));
    }

    private Map<Long, List<StoreMatchParticipant>> participantsByMatch(List<StoreMatch> matches) {
        if (matches.isEmpty()) return Map.of();
        return participantRepository.findByStoreMatchIdIn(matches.stream().map(StoreMatch::getId).toList()).stream()
            .sorted(Comparator.comparing(StoreMatchParticipant::getId))
            .collect(Collectors.groupingBy(StoreMatchParticipant::getStoreMatchId));
    }

    private boolean inContestParty(Long memberId) {
        return partyService.membership(memberId)
            .flatMap(pm -> partyService.findParty(pm.getPartyId()))
            .map(StoreParty::isContest)
            .orElse(false);
    }

    static boolean isToday(Store store, StoreContest contest, LocalDateTime now) {
        return contest.getBusinessDate().equals(StoreClock.businessDate(store, now));
    }

    static boolean isDayOver(Store store, StoreContest contest, LocalDateTime now) {
        return contest.getBusinessDate().isBefore(StoreClock.businessDate(store, now));
    }

    private StoreContest getContest(Long storeId, Long contestId) {
        return contestRepository.findById(contestId)
            .filter(c -> c.getStoreId().equals(storeId))
            .orElseThrow(() -> new StoreException(StoreErrorCode.CONTEST_NOT_FOUND));
    }

    private StoreContest lockContest(Long storeId, Long contestId) {
        return contestRepository.findByIdForUpdate(contestId)
            .filter(c -> c.getStoreId().equals(storeId))
            .orElseThrow(() -> new StoreException(StoreErrorCode.CONTEST_NOT_FOUND));
    }

    private Store getStore(Long storeId) {
        return storeRepository.findById(storeId).orElseThrow(() -> new StoreException(StoreErrorCode.STORE_NOT_FOUND));
    }

    private Store getOpenStore(Long storeId) {
        Store store = getStore(storeId);
        if (store.isClosed()) throw new StoreException(StoreErrorCode.STORE_CLOSED);
        return store;
    }
}
