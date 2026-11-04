package com.board_game_back.Service;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreMatch;
import com.board_game_back.Entity.StoreMember;
import com.board_game_back.Entity.StoreRating;
import com.board_game_back.Entity.StoreSeason;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.StoreMatchParticipantRepository;
import com.board_game_back.Repository.StoreMatchRepository;
import com.board_game_back.Repository.StoreMemberRepository;
import com.board_game_back.Repository.StoreRatingRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Utils.StoreClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 내 매장 기록과 매장 통계: 프로필 매장 탭(7), 운영 끝난 매장의 내 기록(2h), 월간 리포트(8)·어드민 통계(6).
 * 날짜 경계는 전부 매장 영업일. 닉네임은 매장 가입 때 동의한 범위(매장 안 공개)라 STAFF 리포트에 보여도 된다.
 */
@Service
@RequiredArgsConstructor
public class StoreInsightService {

    public static final int HEAT_BINS = 4;
    public static final int HEAT_HOURS = 3;

    private final StoreRepository storeRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreRatingRepository ratingRepository;
    private final StoreMatchRepository matchRepository;
    private final StoreMatchParticipantRepository participantRepository;
    private final BoardGameRepository boardGameRepository;
    private final StoreRankingService rankingService;
    private final StoreAccessService accessService;
    private final StoreReportService reportService;
    private final StorePartyService partyService;
    private final StoreContestService contestService;
    private final StoreEventService eventService;

    // ── 7 프로필 매장 탭 ──

    /** 가입한 매장마다 이번 시즌 기록. 티어는 게임별 점수 중 최고점 */
    public List<StoreDto.StoreProfile> profile(Long memberId) {
        LocalDateTime now = StorePartyService.now();
        List<StoreDto.StoreProfile> result = new ArrayList<>();
        for (StoreMember sm : storeMemberRepository.findByMemberIdOrderByJoinedAtAsc(memberId)) {
            Store store = storeRepository.findById(sm.getStoreId()).orElse(null);
            if (store == null) continue;
            StoreSeason season = rankingService.currentSeason(store, now).orElse(null);
            LocalDate from = season == null ? StoreRankingService.EPOCH : season.getStartDate();
            List<StoreRating> mine = ratingRepository.findByStoreIdAndMemberId(store.getId(), memberId).stream()
                .filter(r -> r.getGames() > 0).toList();
            StoreRating best = mine.stream().max(Comparator.comparingDouble(StoreRating::getRating)).orElse(null);
            int wins = mine.stream().mapToInt(StoreRating::getWins).sum();
            int games = mine.stream().mapToInt(StoreRating::getGames).sum();
            Integer rank = null;
            Integer rankTotal = null;
            if (best != null) {
                List<StoreRating> all = ratingRepository.findByStoreIdAndBoardGameId(store.getId(), best.getBoardGameId());
                List<StoreDto.RankItem> ranked = rankingService.rank(all,
                    rankingService.members(all.stream().map(StoreRating::getMemberId).toList()), memberId);
                rank = ranked.stream().filter(StoreDto.RankItem::me).map(StoreDto.RankItem::rank).findFirst().orElse(null);
                rankTotal = ranked.size();
            }
            StoreRating top = mine.stream().max(Comparator.comparingInt(StoreRating::getGames)).orElse(null);
            String topName = top == null ? null : boardGameRepository.findById(top.getBoardGameId()).map(BoardGame::getName).orElse(null);
            int visits = (int) participantRepository.myMatchesBetween(store.getId(), memberId, from, StoreRankingService.FAR_FUTURE)
                .stream().map(r -> r[3]).distinct().count();
            result.add(new StoreDto.StoreProfile(store.getId(), store.getName(), store.getBranch(), store.isClosed(),
                season == null ? null : season.getName(), best == null ? 500 : best.displayScore(), wins, games - wins,
                rank, rankTotal, topName, top == null ? 0 : top.getGames(), visits,
                reportService.activeNotices(store.getId(), memberId)));
        }
        return result;
    }

    // ── 2h 운영 끝난 매장 ──

    public StoreDto.ClosedRecord closedRecord(Store store, Long memberId) {
        List<StoreRating> mine = ratingRepository.findByStoreIdAndMemberId(store.getId(), memberId).stream()
            .filter(r -> r.getGames() > 0).toList();
        StoreRating best = mine.stream().max(Comparator.comparingDouble(StoreRating::getRating)).orElse(null);
        Integer rank = null;
        if (best != null) {
            List<StoreRating> all = ratingRepository.findByStoreIdAndBoardGameId(store.getId(), best.getBoardGameId());
            rank = rankingService.rank(all, rankingService.members(all.stream().map(StoreRating::getMemberId).toList()), memberId)
                .stream().filter(StoreDto.RankItem::me).map(StoreDto.RankItem::rank).findFirst().orElse(null);
        }
        List<Object[]> matches = participantRepository.myMatchesBetween(store.getId(), memberId,
            StoreRankingService.EPOCH, StoreRankingService.FAR_FUTURE);
        int visits = (int) matches.stream().map(r -> r[3]).distinct().count();
        return new StoreDto.ClosedRecord(rank, matches.size(), visits);
    }

    // ── 8 월간 리포트 · 6 통계 ──

    /** STAFF (이 매장 지정) */
    public StoreDto.MonthlyReport staffReport(Long storeId, Long memberId, String month) {
        accessService.requireStaffOf(storeId, memberId);
        return report(storeId, month);
    }

    /** month "yyyy-MM", 없으면 이번 달(매장 영업일 기준) */
    public StoreDto.MonthlyReport report(Long storeId, String month) {
        Store store = storeRepository.findById(storeId).orElseThrow(() -> new StoreException(StoreErrorCode.STORE_NOT_FOUND));
        YearMonth ym;
        try {
            ym = month == null || month.isBlank() ? YearMonth.from(StoreClock.businessDate(store, StorePartyService.now())) : YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new StoreException(StoreErrorCode.INVALID_INPUT);
        }
        Month cur = month(store, ym);
        Month prev = month(store, ym.minusMonths(1));

        Map<Long, String> gameNames = boardGameRepository.findByIdIn(cur.matches.stream().map(StoreMatch::getBoardGameId).distinct().toList())
            .stream().collect(Collectors.toMap(BoardGame::getId, BoardGame::getName));
        List<StoreDto.GameCount> popular = cur.matches.stream()
            .collect(Collectors.groupingBy(StoreMatch::getBoardGameId, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
            .limit(4)
            .map(e -> new StoreDto.GameCount(gameNames.get(e.getKey()), e.getValue()))
            .toList();

        int openHour = store.getOpenTime().getHour();
        List<Integer> heatHours = new ArrayList<>();
        for (int i = 0; i < HEAT_BINS; i++) heatHours.add((openHour + i * HEAT_HOURS) % 24);
        int[][] heat = new int[7][HEAT_BINS];
        for (StoreMatch m : cur.matches) {
            int hour = StoreClock.toLocal(store, m.getStartedAt()).getHour();
            int bin = Math.min(HEAT_BINS - 1, ((hour - openHour + 24) % 24) / HEAT_HOURS);
            heat[m.getBusinessDate().getDayOfWeek().getValue() - 1][bin]++;
        }
        List<List<Integer>> heatRows = new ArrayList<>();
        for (int[] row : heat) heatRows.add(java.util.Arrays.stream(row).boxed().toList());

        Map<Long, String> names = partyService.nicknames(cur.activity.stream().map(r -> (Long) r[0]).toList());
        List<StoreDto.Regular> regulars = cur.activity.stream()
            .filter(r -> names.get((Long) r[0]) != null)
            .sorted(Comparator.<Object[]>comparingLong(r -> (Long) r[2]).reversed()
                .thenComparing(Comparator.<Object[]>comparingLong(r -> (Long) r[1]).reversed()))
            .limit(3)
            .map(r -> new StoreDto.Regular(names.get((Long) r[0]), (Long) r[2], (Long) r[1]))
            .toList();
        long revisit = cur.activity.stream().filter(r -> (Long) r[2] >= 2).count();
        int revisitRate = cur.activity.isEmpty() ? 0 : (int) Math.round(revisit * 100.0 / cur.activity.size());

        return new StoreDto.MonthlyReport(ym.toString(), new StoreDto.StoreBrief(store.getId(), store.getName(), store.getBranch()),
            cur.matches.size(), prev.matches.size(), cur.activity.size(), prev.activity.size(), cur.newMembers, prev.newMembers,
            revisitRate, popular, heatHours, heatRows, regulars, events(store, ym));
    }

    /** "이번 달 이벤트 결과": 그 달 하루 대회와 겹친 매장 이벤트 */
    private List<StoreDto.ReportEvent> events(Store store, YearMonth ym) {
        List<StoreDto.ReportEvent> result = new ArrayList<>(contestService.reportContests(store, ym.atDay(1), ym.atEndOfMonth()));
        result.addAll(eventService.reportEvents(store.getId(), ym.atDay(1), ym.atEndOfMonth()));
        return result;
    }

    private record Month(List<StoreMatch> matches, List<Object[]> activity, long newMembers) {}

    private Month month(Store store, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<StoreMatch> matches = matchRepository.findByStoreIdAndStatusAndBusinessDateBetweenOrderByEndedAtAsc(
            store.getId(), StoreMatch.Status.RECORDED, from, to);
        List<Object[]> activity = participantRepository.activityBetween(store.getId(), from, to);
        LocalDateTime start = StoreClock.openingAt(store, from);
        LocalDateTime end = StoreClock.openingAt(store, to.plusDays(1));
        long newMembers = storeMemberRepository.countJoinedBetween(store.getId(), start, end);
        return new Month(matches, activity, newMembers);
    }
}
