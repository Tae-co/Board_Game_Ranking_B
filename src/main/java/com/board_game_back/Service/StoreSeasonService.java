package com.board_game_back.Service;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreMatch;
import com.board_game_back.Entity.StoreMatchParticipant;
import com.board_game_back.Entity.StoreRating;
import com.board_game_back.Entity.StoreRatingReset;
import com.board_game_back.Entity.StoreRoom;
import com.board_game_back.Entity.StoreSeason;
import com.board_game_back.Entity.StoreSeasonSnapshot;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.StoreMatchParticipantRepository;
import com.board_game_back.Repository.StoreMatchRepository;
import com.board_game_back.Repository.StoreRatingRepository;
import com.board_game_back.Repository.StoreRatingResetRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Repository.StoreRoomRepository;
import com.board_game_back.Repository.StoreSeasonRepository;
import com.board_game_back.Repository.StoreSeasonSnapshotRepository;
import com.board_game_back.Utils.StoreClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매장 시즌 (plan-store §2.2 시즌, §6.4). 경계는 영업일, 판은 영업일이 속한 시즌에 들어간다.
 *
 * <p>시즌이 끝나면 게임별 순위·MVP를 스냅샷으로 남기고 매장 점수를 지운다(다음 판부터 500). 다음 시즌은 매장의
 * 주기(매월 / 분기 / 직접 지정)로 이어 붙인다. 이번 시즌 시작일을 바꾸거나 신고 초기화를 되돌리면
 * {@link #replay}로 이번 시즌 점수를 처음부터 다시 계산한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreSeasonService {

    public static final List<String> CYCLES = List.of("MONTHLY", "QUARTERLY", "CUSTOM");
    /** 4b 시즌 결과는 끝나고 1주일 */
    public static final int RESULT_DAYS = 7;
    private static final int NAME_MAX = 40;

    private final StoreRepository storeRepository;
    private final StoreSeasonRepository seasonRepository;
    private final StoreSeasonSnapshotRepository snapshotRepository;
    private final StoreRatingRepository ratingRepository;
    private final StoreRatingResetRepository resetRepository;
    private final StoreMatchRepository matchRepository;
    private final StoreMatchParticipantRepository participantRepository;
    private final StoreRoomRepository roomRepository;
    private final BoardGameRepository boardGameRepository;
    private final StoreRankingService rankingService;
    private final StoreEloCalculator eloCalculator;

    // ── 시즌 경계 ──

    /** 끝난 시즌을 마감하고, 오늘을 덮는 시즌이 없으면 주기대로 만든다. 1시간마다 + 처음 조회할 때 */
    @Transactional
    public StoreSeason ensureCurrent(Long storeId, LocalDateTime now) {
        Store store = getStore(storeId);
        LocalDate today = StoreClock.businessDate(store, now);
        List<StoreSeason> seasons = seasonRepository.findByStoreIdOrderByStartDateAsc(storeId);
        for (StoreSeason s : seasons) {
            if (s.getFinalizedAt() == null && s.getEndDate().isBefore(today)) finalizeSeason(store, s, now);
        }
        Optional<StoreSeason> current = seasons.stream().filter(s -> s.covers(today)).findFirst();
        if (current.isPresent()) return current.get();
        StoreSeason last = seasons.isEmpty() ? null : seasons.get(seasons.size() - 1);
        // 끊긴 날이 있거나 처음이면 오늘부터 주기대로
        LocalDate start = last != null && !last.getEndDate().isBefore(today.minusDays(1)) ? last.getEndDate().plusDays(1) : today;
        if (last == null && "MONTHLY".equals(store.getSeasonCycle())) start = today.withDayOfMonth(1);
        LocalDate end = cycleEnd(store.getSeasonCycle(), start);
        if (end.isBefore(today)) end = cycleEnd("MONTHLY", today);
        return seasonRepository.save(new StoreSeason(storeId, defaultName(store.getSeasonCycle(), start), start, end, now));
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 90_000)
    public void tick() {
        LocalDateTime now = StorePartyService.now();
        for (Store store : storeRepository.findByClosedAtIsNull()) {
            try {
                ensureCurrent(store.getId(), now);
            } catch (Exception e) {
                log.error("매장 시즌 경계 처리 실패 store={}", store.getId(), e);
            }
        }
    }

    void finalizeSeason(Store store, StoreSeason season, LocalDateTime now) {
        List<StoreRating> ratings = ratingRepository.findByStoreId(store.getId());
        Map<Long, List<StoreRating>> byGame = ratings.stream().collect(Collectors.groupingBy(StoreRating::getBoardGameId));
        Map<Long, Member> members = rankingService.members(ratings.stream().map(StoreRating::getMemberId).toList());
        List<StoreSeasonSnapshot> rows = new ArrayList<>();
        List<StoreDto.GameRanking> leaders = new ArrayList<>();
        Map<Long, String> names = gameNames(byGame.keySet());
        for (Map.Entry<Long, List<StoreRating>> e : byGame.entrySet()) {
            List<StoreDto.RankItem> ranked = rankingService.rank(e.getValue(), members, null);
            for (StoreDto.RankItem r : ranked) {
                rows.add(new StoreSeasonSnapshot(season.getId(), StoreSeasonSnapshot.Kind.RANK, e.getKey(), r.memberId(),
                    r.nickname(), r.rank(), r.score(), r.games()));
            }
            if (!ranked.isEmpty()) {
                leaders.add(new StoreDto.GameRanking(null, e.getKey(), names.get(e.getKey()), ranked.size(), ranked.subList(0, 1)));
            }
        }
        StoreDto.Mvp mvp = rankingService.mvpBetween(store.getId(), season.getStartDate(), season.getEndDate(), leaders);
        if (mvp != null && mvp.mostVisits() != null) {
            rows.add(new StoreSeasonSnapshot(season.getId(), StoreSeasonSnapshot.Kind.MOST_VISITS, null,
                mvp.mostVisits().memberId(), mvp.mostVisits().nickname(), 1, (int) mvp.mostVisits().count(), null));
        }
        if (mvp != null && mvp.mostGames() != null) {
            rows.add(new StoreSeasonSnapshot(season.getId(), StoreSeasonSnapshot.Kind.MOST_GAMES, null,
                mvp.mostGames().memberId(), mvp.mostGames().nickname(), 1, (int) mvp.mostGames().count(), null));
        }
        snapshotRepository.saveAll(rows);
        ratingRepository.deleteByStoreId(store.getId());
        season.finalizeAt(now);
    }

    // ── 6 어드민 시즌 ──

    @Transactional
    public StoreDto.AdminSeasonResponse adminView(Long storeId) {
        LocalDateTime now = StorePartyService.now();
        StoreSeason current = ensureCurrent(storeId, now);
        Store store = getStore(storeId);
        LocalDate today = StoreClock.businessDate(store, now);
        StoreSeason next = nextOf(storeId, current).orElse(null);
        if (next == null) {
            LocalDate start = current.getEndDate().plusDays(1);
            String cycle = "CUSTOM".equals(store.getSeasonCycle()) ? "MONTHLY" : store.getSeasonCycle();
            next = new StoreSeason(storeId, defaultName(cycle, start), start, cycleEnd(cycle, start), now);
        }
        return new StoreDto.AdminSeasonResponse(info(current, today), info(next, today), store.getSeasonCycle(), today.toString());
    }

    @Transactional
    public StoreDto.AdminSeasonResponse save(Long storeId, StoreDto.SeasonForm form) {
        LocalDateTime now = StorePartyService.now();
        Store store = getStore(storeId);
        StoreSeason current = ensureCurrent(storeId, now);
        LocalDate today = StoreClock.businessDate(store, now);
        String currentName = name(form.currentName());
        LocalDate start = date(form.currentStart());
        LocalDate end = date(form.currentEnd());
        if (start.isAfter(today) || end.isBefore(today) || end.isBefore(start)) throw invalid();
        Optional<StoreSeason> previous = seasonRepository.findByStoreIdOrderByStartDateAsc(storeId).stream()
            .filter(s -> s.getStartDate().isBefore(current.getStartDate())).reduce((a, b) -> b);
        if (previous.isPresent() && !start.isAfter(previous.get().getEndDate())) throw invalid();
        if (form.cycle() == null || !CYCLES.contains(form.cycle())) throw invalid();

        boolean startChanged = !start.equals(current.getStartDate());
        current.update(currentName, start, end);
        store.setSeasonCycle(form.cycle());

        LocalDate nextStart = end.plusDays(1);
        LocalDate nextEnd = "CUSTOM".equals(form.cycle()) ? date(form.nextEnd()) : cycleEnd(form.cycle(), nextStart);
        if (nextEnd.isBefore(nextStart)) throw invalid();
        String nextName = name(form.nextName());
        // 이번 시즌 뒤의 예정 시즌은 하나만 둔다
        List<StoreSeason> future = seasonRepository.findByStoreIdOrderByStartDateAsc(storeId).stream()
            .filter(s -> s.getFinalizedAt() == null && s.getStartDate().isAfter(current.getStartDate()) && !s.getId().equals(current.getId()))
            .toList();
        StoreSeason next = future.isEmpty() ? null : future.get(0);
        future.stream().skip(1).forEach(seasonRepository::delete);
        if (next == null) next = seasonRepository.save(new StoreSeason(storeId, nextName, nextStart, nextEnd, now));
        else next.update(nextName, nextStart, nextEnd);

        if (startChanged) replay(store, current);
        return new StoreDto.AdminSeasonResponse(info(current, today), info(next, today), form.cycle(), today.toString());
    }

    // ── 이번 시즌 점수 다시 계산 ──

    /** 시작일 변경·초기화 되돌리기 뒤. 이번 시즌 기록된 판과 살아 있는 초기화를 시각 순으로 다시 적용한다 */
    @Transactional
    public void replay(Long storeId) {
        Store store = getStore(storeId);
        rankingService.currentSeason(store, StorePartyService.now()).ifPresent(season -> replay(store, season));
    }

    void replay(Store store, StoreSeason season) {
        LocalDateTime seasonStart = StoreClock.openingAt(store, season.getStartDate());
        List<StoreMatch> matches = matchRepository.findByStoreIdAndStatusAndBusinessDateBetweenOrderByEndedAtAsc(
            store.getId(), StoreMatch.Status.RECORDED, season.getStartDate(), StoreRankingService.FAR_FUTURE);
        List<StoreRatingReset> resets = resetRepository.findByStoreIdAndUndoneAtIsNullOrderByResetAtAsc(store.getId()).stream()
            .filter(r -> r.getMemberId() != null && !r.getResetAt().isBefore(seasonStart))
            .toList();
        Map<Long, List<StoreMatchParticipant>> byMatch = matches.isEmpty() ? Map.of()
            : participantRepository.findByStoreMatchIdIn(matches.stream().map(StoreMatch::getId).toList()).stream()
                .collect(Collectors.groupingBy(StoreMatchParticipant::getStoreMatchId));

        Map<String, StoreRating> ratings = new HashMap<>();
        int ri = 0;
        for (StoreMatch m : matches) {
            // 대회 판은 매장 Elo에 안 들어간다
            if (m.isContest()) continue;
            while (ri < resets.size() && resets.get(ri).getResetAt().isBefore(m.getEndedAt())) applyReset(ratings, resets.get(ri++));
            List<StoreMatchParticipant> rated = byMatch.getOrDefault(m.getId(), List.of()).stream()
                .filter(p -> p.getMemberId() != null && p.getPlacement() != null)
                .toList();
            if (rated.isEmpty()) continue;
            List<StoreRating> rs = rated.stream()
                .map(p -> ratings.computeIfAbsent(key(m.getBoardGameId(), p.getMemberId()),
                    k -> new StoreRating(store.getId(), m.getBoardGameId(), p.getMemberId())))
                .toList();
            double[] updated = eloCalculator.calculate(rated.stream()
                .map(p -> {
                    StoreRating r = ratings.get(key(m.getBoardGameId(), p.getMemberId()));
                    return new StoreEloCalculator.Player(r.getRating(), r.getGames(), p.getPlacement());
                })
                .toList());
            for (int i = 0; i < rated.size(); i++) rs.get(i).apply(updated[i], rated.get(i).getPlacement() == 1, m.getEndedAt());
        }
        while (ri < resets.size()) applyReset(ratings, resets.get(ri++));
        ratingRepository.deleteByStoreId(store.getId());
        ratingRepository.flush();
        ratingRepository.saveAll(ratings.values());
    }

    private static void applyReset(Map<String, StoreRating> ratings, StoreRatingReset reset) {
        StoreRating r = ratings.get(key(reset.getBoardGameId(), reset.getMemberId()));
        if (r != null) r.resetRating();
    }

    private static String key(Long gameId, Long memberId) {
        return gameId + ":" + memberId;
    }

    // ── 조회 (3l·4b) ──

    /** 매장 홈·랭킹·TV 머리의 이번 시즌 */
    @Transactional
    public StoreDto.SeasonInfo currentInfo(Long storeId) {
        LocalDateTime now = StorePartyService.now();
        Store store = getStore(storeId);
        if (store.isClosed()) {
            return rankingService.currentSeason(store, now).map(s -> info(s, StoreClock.businessDate(store, now))).orElse(null);
        }
        return info(ensureCurrent(storeId, now), StoreClock.businessDate(store, now));
    }

    /** 3l 매장 게임방 시즌 탭 */
    @Transactional
    public StoreDto.SeasonTabResponse seasonTab(Long storeId, Long storeRoomId, Long memberId) {
        LocalDateTime now = StorePartyService.now();
        Store store = getStore(storeId);
        StoreRoom room = roomRepository.findById(storeRoomId).filter(r -> r.getStoreId().equals(storeId))
            .orElseThrow(() -> new StoreException(StoreErrorCode.ROOM_CLOSED));
        StoreDto.SeasonInfo current = currentInfo(storeId);
        LocalDate from = current == null ? StoreRankingService.EPOCH : LocalDate.parse(current.startDate());

        List<StoreRating> ratings = ratingRepository.findByStoreIdAndBoardGameId(storeId, room.getBoardGameId());
        List<StoreDto.RankItem> ranked = rankingService.rank(ratings,
            rankingService.members(ratings.stream().map(StoreRating::getMemberId).toList()), memberId);
        StoreDto.MySeason mine = ranked.stream().filter(StoreDto.RankItem::me).findFirst().map(me -> {
            int[] places = new int[4];
            int sum = 0;
            int n = 0;
            for (Object[] row : participantRepository.myMatchesBetween(storeId, memberId, from, StoreRankingService.FAR_FUTURE)) {
                if (!room.getBoardGameId().equals(row[1]) || row[2] == null) continue;
                int place = (Integer) row[2];
                places[Math.min(place, 4) - 1]++;
                sum += place;
                n++;
            }
            Double avg = n == 0 ? null : Math.round(sum * 10.0 / n) / 10.0;
            return new StoreDto.MySeason(me.rank(), me.score(), me.games(), places[0], places[1], places[2], places[3], avg);
        }).orElse(null);

        List<StoreSeason> past = seasonRepository.findByStoreIdAndFinalizedAtIsNotNullOrderByStartDateDesc(storeId);
        Map<Long, List<StoreSeasonSnapshot>> snaps = past.isEmpty() ? Map.of()
            : snapshotRepository.findBySeasonIdIn(past.stream().map(StoreSeason::getId).toList()).stream()
                .filter(s -> s.getKind() == StoreSeasonSnapshot.Kind.RANK && room.getBoardGameId().equals(s.getBoardGameId()))
                .collect(Collectors.groupingBy(StoreSeasonSnapshot::getSeasonId));
        LocalDate today = StoreClock.businessDate(store, now);
        List<StoreDto.PastSeason> pastItems = past.stream()
            .filter(s -> snaps.containsKey(s.getId()))
            .map(s -> {
                List<StoreSeasonSnapshot> rows = snaps.get(s.getId()).stream()
                    .sorted(Comparator.comparing(StoreSeasonSnapshot::getRank)).toList();
                Integer myRank = rows.stream().filter(r -> Objects.equals(r.getMemberId(), memberId))
                    .map(StoreSeasonSnapshot::getRank).findFirst().orElse(null);
                List<StoreDto.RankItem> top = rows.stream().limit(3)
                    .map(r -> new StoreDto.RankItem(r.getRank(), r.getMemberId(), r.getNickname(), r.getValue(),
                        r.getGames() == null ? 0 : r.getGames(), Objects.equals(r.getMemberId(), memberId)))
                    .toList();
                return new StoreDto.PastSeason(s.getId(), s.getName(), s.getStartDate().toString(), s.getEndDate().toString(),
                    rows.size(), myRank, top);
            })
            .toList();
        return new StoreDto.SeasonTabResponse(current, ranked.size(), mine, pastItems);
    }

    /** 4b: 가장 최근에 끝난 시즌이 1주일 안에 끝났으면 */
    public StoreDto.SeasonResult recentResult(Long storeId, LocalDateTime now) {
        Store store = getStore(storeId);
        LocalDate today = StoreClock.businessDate(store, now);
        List<StoreSeason> past = seasonRepository.findByStoreIdAndFinalizedAtIsNotNullOrderByStartDateDesc(storeId);
        if (past.isEmpty()) return null;
        StoreSeason last = past.get(0);
        if (ChronoUnit.DAYS.between(last.getEndDate(), today) > RESULT_DAYS) return null;
        List<StoreSeasonSnapshot> rows = snapshotRepository.findBySeasonId(last.getId());
        Map<Long, String> names = gameNames(rows.stream().map(StoreSeasonSnapshot::getBoardGameId).filter(Objects::nonNull).toList());
        List<StoreDto.GameLeader> winners = rows.stream()
            .filter(r -> r.getKind() == StoreSeasonSnapshot.Kind.RANK && Integer.valueOf(1).equals(r.getRank()))
            .sorted(Comparator.comparingInt(StoreSeasonSnapshot::getValue).reversed())
            .map(r -> new StoreDto.GameLeader(r.getBoardGameId(), names.get(r.getBoardGameId()), r.getMemberId(), r.getNickname(), r.getValue()))
            .toList();
        Function<StoreSeasonSnapshot.Kind, StoreDto.MvpItem> mvp = kind -> rows.stream().filter(r -> r.getKind() == kind).findFirst()
            .map(r -> new StoreDto.MvpItem(r.getMemberId(), r.getNickname(), r.getValue())).orElse(null);
        String nextStart = seasonRepository.findByStoreIdOrderByStartDateAsc(storeId).stream()
            .filter(s -> s.getStartDate().isAfter(last.getEndDate())).findFirst()
            .map(s -> s.getStartDate().toString()).orElse(last.getEndDate().plusDays(1).toString());
        if (winners.isEmpty() && mvp.apply(StoreSeasonSnapshot.Kind.MOST_VISITS) == null) return null;
        return new StoreDto.SeasonResult(last.getName(), winners, mvp.apply(StoreSeasonSnapshot.Kind.MOST_VISITS),
            mvp.apply(StoreSeasonSnapshot.Kind.MOST_GAMES), nextStart);
    }

    // ── 공통 ──

    private Optional<StoreSeason> nextOf(Long storeId, StoreSeason current) {
        return seasonRepository.findByStoreIdOrderByStartDateAsc(storeId).stream()
            .filter(s -> s.getFinalizedAt() == null && s.getStartDate().isAfter(current.getEndDate()))
            .findFirst();
    }

    static StoreDto.SeasonInfo info(StoreSeason s, LocalDate today) {
        long left = Math.max(0, ChronoUnit.DAYS.between(today, s.getEndDate()));
        return new StoreDto.SeasonInfo(s.getId(), s.getName(), s.getStartDate().toString(), s.getEndDate().toString(), left);
    }

    static LocalDate cycleEnd(String cycle, LocalDate start) {
        return "QUARTERLY".equals(cycle) ? start.plusMonths(3).minusDays(1) : start.plusMonths(1).minusDays(1);
    }

    /** 기본 이름 "11월 시즌" / "2027 1분기 시즌". 관리자가 바꿀 수 있다 */
    static String defaultName(String cycle, LocalDate start) {
        if ("QUARTERLY".equals(cycle)) return start.getYear() + " " + ((start.getMonthValue() - 1) / 3 + 1) + "분기 시즌";
        return start.getMonthValue() + "월 시즌";
    }

    private Map<Long, String> gameNames(java.util.Collection<Long> ids) {
        return boardGameRepository.findByIdIn(List.copyOf(ids)).stream()
            .collect(Collectors.toMap(BoardGame::getId, BoardGame::getName));
    }

    private static String name(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > NAME_MAX) throw invalid();
        return name;
    }

    private static LocalDate date(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException | NullPointerException e) {
            throw invalid();
        }
    }

    private static StoreException invalid() {
        return new StoreException(StoreErrorCode.INVALID_SEASON);
    }

    private Store getStore(Long storeId) {
        return storeRepository.findById(storeId).orElseThrow(() -> new StoreException(StoreErrorCode.STORE_NOT_FOUND));
    }
}
