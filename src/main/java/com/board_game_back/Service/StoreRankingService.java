package com.board_game_back.Service;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreMatch;
import com.board_game_back.Entity.StoreMatchParticipant;
import com.board_game_back.Entity.StoreParty;
import com.board_game_back.Entity.StoreRating;
import com.board_game_back.Entity.StoreRoom;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.StoreMatchParticipantRepository;
import com.board_game_back.Repository.StoreMatchRepository;
import com.board_game_back.Repository.StoreRatingRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Repository.StoreRoomRepository;
import com.board_game_back.Utils.StoreClock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 게임별 매장 랭킹·MVP·매장 게임방 화면 (2·2b·2c·3). STAFF 역할 계정은 랭킹·MVP에 안 나온다 (plan-store §1 STAFF).
 * 동점이면 가장 최근에 플레이한 사람이 앞 (§1 MVP).
 */
@Service
@RequiredArgsConstructor
public class StoreRankingService {

    public static final int HOME_TOP = 5;
    public static final int ROOM_TOP = 10;
    public static final int FULL_TOP = 20;
    public static final int RECENT_MATCHES = 25;

    private final StoreRepository storeRepository;
    private final StoreRoomRepository storeRoomRepository;
    private final StoreRatingRepository storeRatingRepository;
    private final StoreMatchRepository storeMatchRepository;
    private final StoreMatchParticipantRepository participantRepository;
    private final BoardGameRepository boardGameRepository;
    private final MemberRepository memberRepository;
    private final StorePartyService partyService;
    private final StoreMatchService matchService;

    /** 손님 화면에 보이는 게임방 = 열려 있고 기록 시간이 정해진 게임 (값이 없는 게임은 잠시 닫힘으로 시작, impl §7-5) */
    public List<StoreDto.GameItem> visibleGames(Long storeId) {
        List<StoreRoom> rooms = storeRoomRepository.findByStoreIdAndClosedFalse(storeId);
        Map<Long, BoardGame> games = games(rooms);
        Map<Long, Long> players = countMap(participantRepository.playerCountByRoom(storeId));
        return rooms.stream()
            .filter(r -> games.containsKey(r.getBoardGameId()) && games.get(r.getBoardGameId()).hasRecordMinutes())
            .map(r -> gameItem(r, games.get(r.getBoardGameId()), players.getOrDefault(r.getId(), 0L)))
            // 참여 인원 많은 순 (2c)
            .sorted(Comparator.comparingLong(StoreDto.GameItem::playerCount).reversed()
                .thenComparing(StoreDto.GameItem::boardGameId))
            .toList();
    }

    public List<StoreDto.GameRanking> homeRankings(Long storeId, List<StoreDto.GameItem> games, Long memberId) {
        Map<Long, List<StoreRating>> byGame = storeRatingRepository
            .findByStoreIdAndBoardGameIdIn(storeId, games.stream().map(StoreDto.GameItem::boardGameId).toList())
            .stream().collect(Collectors.groupingBy(StoreRating::getBoardGameId));
        Map<Long, Member> members = members(byGame.values().stream().flatMap(List::stream).map(StoreRating::getMemberId).toList());
        List<StoreDto.GameRanking> result = new ArrayList<>();
        for (StoreDto.GameItem g : games) {
            List<StoreDto.RankItem> ranked = rank(byGame.getOrDefault(g.boardGameId(), List.of()), members, memberId);
            if (ranked.isEmpty()) continue;
            result.add(new StoreDto.GameRanking(g.storeRoomId(), g.boardGameId(), g.name(), ranked.size(),
                ranked.subList(0, Math.min(HOME_TOP, ranked.size()))));
        }
        return result;
    }

    public StoreDto.Mvp mvp(Long storeId, List<StoreDto.GameRanking> rankings) {
        List<Object[]> rows = participantRepository.activityByStore(storeId);
        Map<Long, Member> members = members(rows.stream().map(r -> (Long) r[0]).toList());
        rows = rows.stream().filter(r -> isRankable(members.get((Long) r[0]))).toList();
        Comparator<Object[]> recent = Comparator.comparing(r -> (LocalDateTime) r[3], Comparator.nullsFirst(Comparator.naturalOrder()));
        StoreDto.MvpItem visits = rows.stream()
            .max(Comparator.<Object[]>comparingLong(r -> (Long) r[2]).thenComparing(recent))
            .map(r -> new StoreDto.MvpItem((Long) r[0], members.get((Long) r[0]).getNickname(), (Long) r[2]))
            .orElse(null);
        StoreDto.MvpItem games = rows.stream()
            .max(Comparator.<Object[]>comparingLong(r -> (Long) r[1]).thenComparing(recent))
            .map(r -> new StoreDto.MvpItem((Long) r[0], members.get((Long) r[0]).getNickname(), (Long) r[1]))
            .orElse(null);
        List<StoreDto.GameLeader> leaders = rankings.stream()
            .map(g -> {
                StoreDto.RankItem top = g.top().get(0);
                return new StoreDto.GameLeader(g.boardGameId(), g.name(), top.memberId(), top.nickname(), top.score());
            })
            .toList();
        if (visits == null && leaders.isEmpty()) return null;
        return new StoreDto.Mvp(visits, games, leaders);
    }

    /** 2b 게임별 매장 랭킹 전체 (상위 20 + 내 순위) */
    public StoreDto.RankingResponse ranking(Long storeId, Long storeRoomId, Long memberId) {
        StoreRoom room = room(storeId, storeRoomId);
        BoardGame game = boardGameRepository.findById(room.getBoardGameId()).orElseThrow();
        List<StoreRating> ratings = storeRatingRepository.findByStoreIdAndBoardGameId(storeId, game.getId());
        List<StoreDto.RankItem> ranked = rank(ratings, members(ratings.stream().map(StoreRating::getMemberId).toList()), memberId);
        StoreDto.RankItem me = ranked.stream().filter(StoreDto.RankItem::me).findFirst().orElse(null);
        return new StoreDto.RankingResponse(room.getId(), game.getId(), game.getName(), ranked.size(),
            ranked.subList(0, Math.min(FULL_TOP, ranked.size())), me);
    }

    /** 3 매장 게임방 */
    public StoreDto.RoomResponse room(Long storeId, Long storeRoomId, Long memberId) {
        LocalDateTime now = StorePartyService.now();
        Store store = storeRepository.findById(storeId).orElseThrow(() -> new StoreException(StoreErrorCode.STORE_NOT_FOUND));
        StoreRoom room = room(storeId, storeRoomId);
        BoardGame game = boardGameRepository.findById(room.getBoardGameId()).orElseThrow();
        List<StoreRating> ratings = storeRatingRepository.findByStoreIdAndBoardGameId(storeId, game.getId());
        List<StoreDto.RankItem> ranked = rank(ratings, members(ratings.stream().map(StoreRating::getMemberId).toList()), memberId);

        String block = matchService.startBlock(store, room, game, memberId, now);
        StoreDto.PartyBrief party = null;
        String busyGame = null;
        List<String> expired = List.of();
        StoreParty p = partyService.membership(memberId).flatMap(pm -> partyService.findParty(pm.getPartyId()))
            .filter(sp -> sp.isActive() && sp.getStoreId().equals(storeId))
            .orElse(null);
        if (p != null) {
            party = partyService.brief(p);
            expired = partyService.expiredNicknames(p.getId(), now);
            busyGame = partyService.openMatch(p.getId())
                .flatMap(m -> boardGameRepository.findById(m.getBoardGameId()))
                .map(BoardGame::getName).orElse(null);
        }
        String closesAt = StoreClock.isOpen(store, now)
            ? StoreDto.iso(StoreClock.closingAt(store, now)) : null;
        return new StoreDto.RoomResponse(new StoreDto.StoreBrief(store.getId(), store.getName(), store.getBranch()),
            StoreDto.hhmm(store.getOpenTime()), StoreDto.hhmm(store.getCloseTime()),
            gameItem(room, game, ranked.size()), room.isClosed(),
            ranked.subList(0, Math.min(ROOM_TOP, ranked.size())),
            new StoreDto.StartState(block, party, busyGame, closesAt, expired));
    }

    /** 3 매치기록 탭: 최근 25경기 */
    public List<StoreDto.MatchSummary> recentMatches(Long storeId, Long storeRoomId) {
        room(storeId, storeRoomId);
        List<StoreMatch> matches = storeMatchRepository.findByStoreRoomIdAndStatusOrderByEndedAtDesc(
            storeRoomId, StoreMatch.Status.RECORDED, PageRequest.of(0, RECENT_MATCHES));
        Map<Long, List<StoreMatchParticipant>> byMatch = matches.isEmpty() ? Map.of()
            : participantRepository.findByStoreMatchIdIn(matches.stream().map(StoreMatch::getId).toList()).stream()
                .collect(Collectors.groupingBy(StoreMatchParticipant::getStoreMatchId));
        Map<Long, String> names = partyService.nicknames(byMatch.values().stream().flatMap(List::stream)
            .map(StoreMatchParticipant::getMemberId).filter(Objects::nonNull).toList());
        return matches.stream()
            .map(m -> new StoreDto.MatchSummary(m.getId(), StoreDto.iso(m.getEndedAt()),
                byMatch.getOrDefault(m.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(StoreMatchParticipant::getPlacement, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(p -> StoreMatchService.participantItem(p, names, true))
                    .toList()))
            .toList();
    }

    // ── 공통 ──

    List<StoreDto.RankItem> rank(Collection<StoreRating> ratings, Map<Long, Member> members, Long memberId) {
        List<StoreRating> sorted = ratings.stream()
            .filter(r -> r.getGames() > 0 && isRankable(members.get(r.getMemberId())))
            .sorted(Comparator.comparingDouble(StoreRating::getRating).reversed()
                .thenComparing(StoreRating::getLastPlayedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        List<StoreDto.RankItem> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            StoreRating r = sorted.get(i);
            result.add(new StoreDto.RankItem(i + 1, r.getMemberId(), members.get(r.getMemberId()).getNickname(),
                r.displayScore(), r.getGames(), r.getMemberId().equals(memberId)));
        }
        return result;
    }

    private static boolean isRankable(Member m) {
        return m != null && !StoreAccessService.STAFF_ROLE.equals(m.getRole());
    }

    private Map<Long, Member> members(Collection<Long> ids) {
        Set<Long> unique = new HashSet<>(ids);
        return memberRepository.findAllById(unique).stream().collect(Collectors.toMap(Member::getId, Function.identity()));
    }

    private Map<Long, BoardGame> games(List<StoreRoom> rooms) {
        return boardGameRepository.findByIdIn(rooms.stream().map(StoreRoom::getBoardGameId).toList()).stream()
            .collect(Collectors.toMap(BoardGame::getId, Function.identity()));
    }

    private StoreRoom room(Long storeId, Long storeRoomId) {
        return storeRoomRepository.findById(storeRoomId)
            .filter(r -> r.getStoreId().equals(storeId))
            .orElseThrow(() -> new StoreException(StoreErrorCode.ROOM_CLOSED));
    }

    static StoreDto.GameItem gameItem(StoreRoom r, BoardGame g, long playerCount) {
        return new StoreDto.GameItem(r.getId(), g.getId(), g.getName(), g.getImageUrl(), g.getMinPlayers(),
            g.getMaxPlayers(), g.getMinMinutes(), g.getMaxMinutes(), playerCount);
    }

    private static Map<Long, Long> countMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) map.put((Long) row[0], (Long) row[1]);
        return map;
    }
}
