package com.board_game_back.Service;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.StoreMatch;
import com.board_game_back.Entity.StoreMatchParticipant;
import com.board_game_back.Entity.StoreRating;
import com.board_game_back.Entity.StoreRatingReset;
import com.board_game_back.Entity.StoreReport;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.StoreMatchParticipantRepository;
import com.board_game_back.Repository.StoreMatchRepository;
import com.board_game_back.Repository.StoreMemberRepository;
import com.board_game_back.Repository.StoreRatingRepository;
import com.board_game_back.Repository.StoreRatingResetRepository;
import com.board_game_back.Repository.StoreReportRepository;
import com.board_game_back.Repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경기 신고(3g)·신고 확인과 점수 초기화(6b)·당사자 안내(2 안내 카드·7 프로필 줄). 기획 plan-store §6.5.
 *
 * <p>6b는 (게임, 신고된 경기의 1등) 묶음으로 본다. 초기화는 그 묶음의 신고된 경기를 같이 한 사람 중 고른 사람의
 * 이 매장 그 게임 점수를 500으로 돌린다. 되돌리면 이번 시즌 점수를 다시 계산해 그 뒤 경기를 돌린 점수 위에 쌓는다.
 */
@Service
@RequiredArgsConstructor
public class StoreReportService {

    public static final int RECENT_MATCHES = 10;
    private static final int MEMO_MAX = 300;

    private final StoreRepository storeRepository;
    private final StoreReportRepository reportRepository;
    private final StoreRatingResetRepository resetRepository;
    private final StoreRatingRepository ratingRepository;
    private final StoreMatchRepository matchRepository;
    private final StoreMatchParticipantRepository participantRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final BoardGameRepository boardGameRepository;
    private final StoreRankingService rankingService;
    private final StorePartyService partyService;
    private final StoreSeasonService seasonService;

    // ── 3g 신고 ──

    @Transactional
    public void report(Long matchId, Long memberId, StoreDto.ReportRequest request) {
        StoreMatch match = matchRepository.findById(matchId)
            .filter(m -> m.getStatus() == StoreMatch.Status.RECORDED && !m.isContest())
            .orElseThrow(() -> new StoreException(StoreErrorCode.MATCH_NOT_FOUND));
        storeMemberRepository.findByStoreIdAndMemberId(match.getStoreId(), memberId)
            .orElseThrow(() -> new StoreException(StoreErrorCode.NOT_MEMBER));
        if (reportRepository.findByStoreMatchIdAndReporterMemberId(matchId, memberId).isPresent()) {
            throw new StoreException(StoreErrorCode.ALREADY_REPORTED);
        }
        StoreReport.Reason reason;
        try {
            reason = StoreReport.Reason.valueOf(request.reason());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new StoreException(StoreErrorCode.INVALID_INPUT);
        }
        String memo = request.memo() == null ? null : request.memo().trim();
        if (memo != null && memo.length() > MEMO_MAX) memo = memo.substring(0, MEMO_MAX);
        if (memo != null && memo.isEmpty()) memo = null;
        Long target = participantRepository.findByStoreMatchIdOrderByIdAsc(matchId).stream()
            .filter(p -> Integer.valueOf(1).equals(p.getPlacement()) && p.getMemberId() != null)
            .map(StoreMatchParticipant::getMemberId).findFirst().orElse(null);
        reportRepository.save(new StoreReport(match.getStoreId(), matchId, match.getBoardGameId(), target, memberId,
            reason, memo, StorePartyService.now()));
    }

    // ── 6 신고 목록 · 6b 확인 ──

    public List<StoreDto.AdminReportGroup> groups(Long storeId) {
        Map<String, List<StoreReport>> byGroup = grouped(storeId);
        Map<Long, String> games = gameNames(byGroup.values().stream().map(l -> l.get(0).getBoardGameId()).toList());
        Map<Long, String> names = partyService.nicknames(byGroup.values().stream()
            .map(l -> l.get(0).getTargetMemberId()).filter(Objects::nonNull).toList());
        return byGroup.values().stream()
            .map(list -> {
                StoreReport first = list.get(0);
                LocalDateTime last = list.stream().map(StoreReport::getCreatedAt).max(Comparator.naturalOrder()).orElseThrow();
                return new StoreDto.AdminReportGroup(first.getBoardGameId(), games.get(first.getBoardGameId()),
                    first.getTargetMemberId(), StoreMatchService.nameOf(names, first.getTargetMemberId()),
                    list.size(), StoreDto.iso(last), status(list));
            })
            .sorted(Comparator.comparing((StoreDto.AdminReportGroup g) -> !"OPEN".equals(g.status()))
                .thenComparing(StoreDto.AdminReportGroup::lastAt, Comparator.reverseOrder()))
            .toList();
    }

    public StoreDto.AdminReportDetail detail(Long storeId, Long boardGameId, Long targetMemberId) {
        List<StoreReport> reports = group(storeId, boardGameId, targetMemberId);
        String gameName = boardGameRepository.findById(boardGameId).map(BoardGame::getName).orElse("");

        // 대상의 이번 매장 점수·순위
        List<StoreRating> ratings = ratingRepository.findByStoreIdAndBoardGameId(storeId, boardGameId);
        List<StoreDto.RankItem> ranked = rankingService.rank(ratings,
            rankingService.members(ratings.stream().map(StoreRating::getMemberId).toList()), targetMemberId);
        StoreDto.RankItem me = targetMemberId == null ? null : ranked.stream().filter(StoreDto.RankItem::me).findFirst().orElse(null);

        // 대상의 최근 이 게임 경기
        List<StoreMatch> recentMatches = targetMemberId == null ? List.of()
            : matchRepository.findByStoreIdAndStatus(storeId, StoreMatch.Status.RECORDED).stream()
                .filter(m -> m.getBoardGameId().equals(boardGameId) && !m.isContest())
                .sorted(Comparator.comparing(StoreMatch::getEndedAt).reversed())
                .toList();
        Map<Long, List<StoreMatchParticipant>> byMatch = recentMatches.isEmpty() ? Map.of()
            : participantRepository.findByStoreMatchIdIn(recentMatches.stream().map(StoreMatch::getId).toList()).stream()
                .collect(Collectors.groupingBy(StoreMatchParticipant::getStoreMatchId));
        List<StoreMatch> mine = recentMatches.stream()
            .filter(m -> byMatch.getOrDefault(m.getId(), List.of()).stream().anyMatch(p -> targetMemberId.equals(p.getMemberId())))
            .toList();

        // 후보 = 신고된 경기를 같이 한 사람
        Set<Long> reportedMatchIds = reports.stream().map(StoreReport::getStoreMatchId).collect(Collectors.toSet());
        Set<Long> candidateIds = new HashSet<>();
        participantRepository.findByStoreMatchIdIn(reportedMatchIds).stream()
            .map(StoreMatchParticipant::getMemberId).filter(Objects::nonNull).forEach(candidateIds::add);
        Set<Long> nameIds = new HashSet<>(candidateIds);
        mine.forEach(m -> byMatch.get(m.getId()).forEach(p -> { if (p.getMemberId() != null) nameIds.add(p.getMemberId()); }));
        Map<Long, String> names = partyService.nicknames(nameIds);

        List<StoreDto.TargetMatch> recent = mine.stream().limit(RECENT_MATCHES)
            .map(m -> {
                List<StoreMatchParticipant> ps = byMatch.get(m.getId());
                Integer place = ps.stream().filter(p -> targetMemberId.equals(p.getMemberId()))
                    .map(StoreMatchParticipant::getPlacement).findFirst().orElse(null);
                List<String> others = ps.stream().filter(p -> !targetMemberId.equals(p.getMemberId()))
                    .map(p -> StoreMatchService.nameOf(names, p.getMemberId())).toList();
                return new StoreDto.TargetMatch(StoreDto.iso(m.getEndedAt()), others, place);
            })
            .toList();
        long wins = mine.stream().filter(m -> byMatch.get(m.getId()).stream()
            .anyMatch(p -> targetMemberId.equals(p.getMemberId()) && Integer.valueOf(1).equals(p.getPlacement()))).count();

        List<StoreDto.ResetCandidate> candidates = candidateIds.stream()
            .map(id -> new StoreDto.ResetCandidate(id, StoreMatchService.nameOf(names, id), id.equals(targetMemberId),
                id.equals(targetMemberId) ? mine.size()
                    : mine.stream().filter(m -> byMatch.get(m.getId()).stream().anyMatch(p -> id.equals(p.getMemberId()))).count()))
            .sorted(Comparator.comparing((StoreDto.ResetCandidate c) -> !c.target()).thenComparing(StoreDto.ResetCandidate::together, Comparator.reverseOrder()))
            .toList();

        String status = status(reports);
        String batch = reports.stream().map(StoreReport::getResolutionBatch).filter(Objects::nonNull).findFirst().orElse(null);
        List<StoreDto.ResetResult> resets = batch == null ? List.of() : resetRepository.findByBatch(batch).stream()
            .map(r -> new StoreDto.ResetResult(StoreMatchService.nameOf(names, r.getMemberId()), r.getBeforeRating()))
            .toList();
        LocalDateTime resolvedAt = reports.stream().map(StoreReport::getResolvedAt).filter(Objects::nonNull).findFirst().orElse(null);
        return new StoreDto.AdminReportDetail(boardGameId, gameName, targetMemberId, StoreMatchService.nameOf(names, targetMemberId),
            me == null ? null : me.rank(), me == null ? null : me.score(), me == null ? null : me.games(),
            reports.stream().sorted(Comparator.comparing(StoreReport::getCreatedAt).reversed())
                .map(r -> new StoreDto.ReportItem(r.getReason().name(), r.getMemo(), StoreDto.iso(r.getCreatedAt()))).toList(),
            recent, wins, mine.size(), candidates, status, StoreDto.iso(resolvedAt), batch, resets);
    }

    /** 고른 사람의 이 매장 그 게임 점수를 500으로 */
    @Transactional
    public StoreDto.AdminReportDetail reset(Long storeId, Long boardGameId, Long targetMemberId, List<Long> memberIds) {
        List<StoreReport> reports = openGroup(storeId, boardGameId, targetMemberId);
        if (memberIds == null || memberIds.isEmpty()) throw new StoreException(StoreErrorCode.INVALID_INPUT);
        Set<Long> matchIds = reports.stream().map(StoreReport::getStoreMatchId).collect(Collectors.toSet());
        Set<Long> allowed = participantRepository.findByStoreMatchIdIn(matchIds).stream()
            .map(StoreMatchParticipant::getMemberId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!allowed.containsAll(memberIds)) throw new StoreException(StoreErrorCode.NOT_IN_MATCH);
        LocalDateTime now = StorePartyService.now();
        String batch = UUID.randomUUID().toString();
        Long latestMatch = reports.stream().max(Comparator.comparing(StoreReport::getCreatedAt)).map(StoreReport::getStoreMatchId).orElse(null);
        Map<Long, StoreRating> ratings = ratingRepository.findByStoreIdAndBoardGameId(storeId, boardGameId).stream()
            .collect(Collectors.toMap(StoreRating::getMemberId, r -> r, (a, b) -> a));
        for (Long memberId : new LinkedHashSet<>(memberIds)) {
            StoreRating r = ratings.get(memberId);
            int before = r == null ? 500 : r.displayScore();
            if (r != null) r.resetRating();
            resetRepository.save(new StoreRatingReset(storeId, boardGameId, memberId, latestMatch, before, batch, now));
        }
        reports.forEach(rep -> rep.resolve(StoreReport.Status.RESET, batch, now));
        return detail(storeId, boardGameId, targetMemberId);
    }

    @Transactional
    public StoreDto.AdminReportDetail close(Long storeId, Long boardGameId, Long targetMemberId) {
        LocalDateTime now = StorePartyService.now();
        openGroup(storeId, boardGameId, targetMemberId).forEach(r -> r.resolve(StoreReport.Status.CLOSED, null, now));
        return detail(storeId, boardGameId, targetMemberId);
    }

    /** 초기화 되돌리기: 그 묶음의 초기화를 지우고 이번 시즌 점수를 다시 계산 */
    @Transactional
    public StoreDto.AdminReportDetail undo(Long storeId, Long boardGameId, Long targetMemberId) {
        List<StoreReport> reports = group(storeId, boardGameId, targetMemberId);
        String batch = reports.stream().filter(r -> r.getStatus() == StoreReport.Status.RESET)
            .map(StoreReport::getResolutionBatch).filter(Objects::nonNull).findFirst()
            .orElseThrow(() -> new StoreException(StoreErrorCode.REPORT_NOT_FOUND));
        LocalDateTime now = StorePartyService.now();
        resetRepository.findByBatch(batch).forEach(r -> r.undo(now));
        resetRepository.flush();
        seasonService.replay(storeId);
        return detail(storeId, boardGameId, targetMemberId);
    }

    // ── 당사자 안내 ──

    /** 2 매장 홈 맨 위 안내 카드: 아직 안 닫은 가장 최근 초기화 */
    public StoreDto.ResetNotice unseenNotice(Long storeId, Long memberId) {
        return resetRepository.findByMemberIdAndUndoneAtIsNull(memberId).stream()
            .filter(r -> r.getStoreId().equals(storeId) && r.getSeenAt() == null)
            .max(Comparator.comparing(StoreRatingReset::getResetAt))
            .map(this::notice)
            .orElse(null);
    }

    @Transactional
    public void markSeen(Long storeId, Long memberId) {
        LocalDateTime now = StorePartyService.now();
        resetRepository.findByMemberIdAndUndoneAtIsNull(memberId).stream()
            .filter(r -> r.getStoreId().equals(storeId))
            .forEach(r -> r.markSeen(now));
    }

    /** 7 프로필: 살아 있는 초기화 전부 */
    public List<StoreDto.ResetNotice> activeNotices(Long storeId, Long memberId) {
        return resetRepository.findByMemberIdAndUndoneAtIsNull(memberId).stream()
            .filter(r -> r.getStoreId().equals(storeId))
            .sorted(Comparator.comparing(StoreRatingReset::getResetAt).reversed())
            .map(this::notice)
            .toList();
    }

    private StoreDto.ResetNotice notice(StoreRatingReset r) {
        String game = boardGameRepository.findById(r.getBoardGameId()).map(BoardGame::getName).orElse("");
        String matchAt = r.getStoreMatchId() == null ? null
            : matchRepository.findById(r.getStoreMatchId()).map(m -> StoreDto.iso(m.getEndedAt())).orElse(null);
        return new StoreDto.ResetNotice(r.getId(), game, matchAt, StoreDto.iso(r.getResetAt()));
    }

    // ── 공통 ──

    private Map<String, List<StoreReport>> grouped(Long storeId) {
        Map<String, List<StoreReport>> map = new LinkedHashMap<>();
        for (StoreReport r : reportRepository.findByStoreIdOrderByCreatedAtDesc(storeId)) {
            map.computeIfAbsent(r.getBoardGameId() + ":" + r.getTargetMemberId(), k -> new ArrayList<>()).add(r);
        }
        return map;
    }

    /** 한 묶음. 처리가 끝난 뒤 새 신고가 들어왔으면 새 신고만 (처리 전 묶음이 우선) */
    private List<StoreReport> group(Long storeId, Long boardGameId, Long targetMemberId) {
        List<StoreReport> all = reportRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
            .filter(r -> r.getBoardGameId().equals(boardGameId) && Objects.equals(r.getTargetMemberId(), targetMemberId))
            .toList();
        if (all.isEmpty()) throw new StoreException(StoreErrorCode.REPORT_NOT_FOUND);
        List<StoreReport> open = all.stream().filter(r -> r.getStatus() == StoreReport.Status.OPEN).toList();
        if (!open.isEmpty()) return open;
        String batch = all.get(0).getResolutionBatch();
        LocalDateTime resolvedAt = all.get(0).getResolvedAt();
        return all.stream().filter(r -> Objects.equals(r.getResolutionBatch(), batch) && Objects.equals(r.getResolvedAt(), resolvedAt)).toList();
    }

    private List<StoreReport> openGroup(Long storeId, Long boardGameId, Long targetMemberId) {
        List<StoreReport> reports = group(storeId, boardGameId, targetMemberId);
        if (reports.stream().anyMatch(r -> r.getStatus() != StoreReport.Status.OPEN)) {
            throw new StoreException(StoreErrorCode.REPORT_NOT_FOUND);
        }
        return reports;
    }

    private String status(List<StoreReport> reports) {
        if (reports.stream().anyMatch(r -> r.getStatus() == StoreReport.Status.OPEN)) return "OPEN";
        StoreReport first = reports.get(0);
        if (first.getStatus() == StoreReport.Status.CLOSED) return "CLOSED";
        boolean undone = first.getResolutionBatch() != null
            && resetRepository.findByBatch(first.getResolutionBatch()).stream().noneMatch(StoreRatingReset::isActive);
        return undone ? "UNDONE" : "RESET";
    }

    private Map<Long, String> gameNames(List<Long> ids) {
        return boardGameRepository.findByIdIn(ids).stream().collect(Collectors.toMap(BoardGame::getId, BoardGame::getName));
    }
}
