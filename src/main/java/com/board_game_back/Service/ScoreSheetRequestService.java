package com.board_game_back.Service;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.ScoreSheetGameRequest;
import com.board_game_back.Entity.ScoreSheetRequestEntry;
import com.board_game_back.Entity.Store;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.ScoreSheetGameRequestRepository;
import com.board_game_back.Repository.ScoreSheetRequestEntryRepository;
import com.board_game_back.Repository.StoreMemberRepository;
import com.board_game_back.Repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점수판 요청 (2g 손님 · 6c 관리자). 같은 게임 이름(공백·대소문자 무시)은 한 줄로 묶고,
 * 요청 수 = 요청하거나 공감한 사람 수. 알림은 없다 — 상태는 "내 요청"에서 본다.
 */
@Service
@RequiredArgsConstructor
public class ScoreSheetRequestService {

    public static final int POPULAR = 10;
    private static final int NAME_MAX = 60;
    private static final int TEXT_MAX = 2000;

    private final ScoreSheetGameRequestRepository gameRequestRepository;
    private final ScoreSheetRequestEntryRepository entryRepository;
    private final StoreRepository storeRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StorePartyService partyService;

    // ── 2g ──

    public StoreDto.ScoreRequestsResponse view(Long memberId) {
        List<ScoreSheetGameRequest> games = gameRequestRepository.findAll();
        List<ScoreSheetRequestEntry> entries = entryRepository.findAll();
        Map<Long, Set<Long>> people = people(entries);
        Set<Long> votedByMe = entries.stream()
            .filter(e -> e.getKind() == ScoreSheetRequestEntry.Kind.VOTE && memberId.equals(e.getMemberId()))
            .map(ScoreSheetRequestEntry::getGameRequestId).collect(Collectors.toSet());
        List<StoreDto.ScoreRequestItem> popular = games.stream()
            .filter(g -> g.getStatus() != ScoreSheetGameRequest.Status.ADDED)
            .sorted(Comparator.comparingInt((ScoreSheetGameRequest g) -> people.getOrDefault(g.getId(), Set.of()).size()).reversed()
                .thenComparing(ScoreSheetGameRequest::getLastRequestedAt, Comparator.reverseOrder()))
            .limit(POPULAR)
            .map(g -> new StoreDto.ScoreRequestItem(g.getId(), g.getName(), people.getOrDefault(g.getId(), Set.of()).size(),
                votedByMe.contains(g.getId())))
            .toList();
        Map<Long, ScoreSheetGameRequest> byId = games.stream().collect(Collectors.toMap(ScoreSheetGameRequest::getId, Function.identity()));
        List<StoreDto.MyScoreRequest> mine = entryRepository
            .findByMemberIdAndKindOrderByCreatedAtDesc(memberId, ScoreSheetRequestEntry.Kind.REQUEST).stream()
            .filter(e -> byId.containsKey(e.getGameRequestId()))
            .map(e -> {
                ScoreSheetGameRequest g = byId.get(e.getGameRequestId());
                return new StoreDto.MyScoreRequest(g.getId(), g.getName(), g.getStatus().name(), StoreDto.iso(e.getCreatedAt()));
            })
            .toList();
        return new StoreDto.ScoreRequestsResponse(popular, mine);
    }

    @Transactional
    public StoreDto.ScoreRequestsResponse request(Long memberId, StoreDto.ScoreRequestForm form) {
        String name = form == null || form.name() == null ? "" : form.name().trim();
        if (name.isEmpty() || name.length() > NAME_MAX) throw new StoreException(StoreErrorCode.INVALID_INPUT);
        LocalDateTime now = StorePartyService.now();
        String normalized = ScoreSheetGameRequest.normalize(name);
        ScoreSheetGameRequest game = gameRequestRepository.findByNormalizedName(normalized)
            .orElseGet(() -> gameRequestRepository.save(new ScoreSheetGameRequest(name, normalized, now)));
        game.touch(now);
        Long storeId = form.storeId() != null && storeRepository.existsById(form.storeId()) ? form.storeId() : null;
        if (entryRepository.findByGameRequestIdAndMemberIdAndKind(game.getId(), memberId, ScoreSheetRequestEntry.Kind.REQUEST).isEmpty()) {
            entryRepository.save(new ScoreSheetRequestEntry(game.getId(), memberId, storeId, ScoreSheetRequestEntry.Kind.REQUEST,
                clip(form.howToScore()), clip(form.imageUrl()), now));
        }
        return view(memberId);
    }

    /** 공감 누르기 / 취소 */
    @Transactional
    public StoreDto.ScoreRequestsResponse toggleVote(Long memberId, Long gameRequestId, Long storeId) {
        ScoreSheetGameRequest game = gameRequestRepository.findById(gameRequestId)
            .orElseThrow(() -> new StoreException(StoreErrorCode.REQUEST_NOT_FOUND));
        entryRepository.findByGameRequestIdAndMemberIdAndKind(game.getId(), memberId, ScoreSheetRequestEntry.Kind.VOTE)
            .ifPresentOrElse(entryRepository::delete, () -> {
                Long sid = storeId != null && storeRepository.existsById(storeId) ? storeId : null;
                entryRepository.save(new ScoreSheetRequestEntry(game.getId(), memberId, sid, ScoreSheetRequestEntry.Kind.VOTE,
                    null, null, StorePartyService.now()));
            });
        return view(memberId);
    }

    // ── 6c ──

    public List<StoreDto.AdminScoreRequest> adminList() {
        List<ScoreSheetGameRequest> games = gameRequestRepository.findAll();
        List<ScoreSheetRequestEntry> entries = entryRepository.findAll();
        Map<Long, Set<Long>> people = people(entries);
        Map<Long, List<ScoreSheetRequestEntry>> byGame = entries.stream().collect(Collectors.groupingBy(ScoreSheetRequestEntry::getGameRequestId));
        Map<Long, Store> stores = storeRepository.findAll().stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        Map<Long, String> names = partyService.nicknames(entries.stream().map(ScoreSheetRequestEntry::getMemberId).filter(Objects::nonNull).toList());
        return games.stream()
            .map(g -> {
                List<ScoreSheetRequestEntry> es = byGame.getOrDefault(g.getId(), List.of());
                Map<String, Set<Long>> perStore = new LinkedHashMap<>();
                es.stream().filter(e -> e.getStoreId() != null && stores.containsKey(e.getStoreId()))
                    .forEach(e -> perStore.computeIfAbsent(storeLabel(stores.get(e.getStoreId())), k -> new HashSet<>()).add(e.getMemberId()));
                List<StoreDto.StoreCount> storeCounts = perStore.entrySet().stream()
                    .map(e -> new StoreDto.StoreCount(e.getKey(), e.getValue().size()))
                    .sorted(Comparator.comparingLong(StoreDto.StoreCount::count).reversed())
                    .toList();
                List<ScoreSheetRequestEntry> requests = es.stream().filter(e -> e.getKind() == ScoreSheetRequestEntry.Kind.REQUEST)
                    .sorted(Comparator.comparing(ScoreSheetRequestEntry::getCreatedAt).reversed()).toList();
                List<StoreDto.ScoreNote> notes = requests.stream()
                    .filter(e -> e.getHowToScore() != null && !e.getHowToScore().isBlank())
                    .map(e -> {
                        Store store = e.getStoreId() == null ? null : stores.get(e.getStoreId());
                        boolean visitor = store != null && e.getMemberId() != null
                            && storeMemberRepository.findByStoreIdAndMemberId(store.getId(), e.getMemberId()).isEmpty();
                        return new StoreDto.ScoreNote(e.getHowToScore(), StoreMatchService.nameOf(names, e.getMemberId()),
                            store == null ? null : storeLabel(store), visitor);
                    })
                    .toList();
                List<String> photos = requests.stream().map(ScoreSheetRequestEntry::getImageUrl)
                    .filter(u -> u != null && !u.isBlank()).toList();
                return new StoreDto.AdminScoreRequest(g.getId(), g.getName(), g.getStatus().name(),
                    people.getOrDefault(g.getId(), Set.of()).size(), storeCounts, StoreDto.iso(g.getLastRequestedAt()), notes, photos);
            })
            .sorted(Comparator.comparingLong(StoreDto.AdminScoreRequest::count).reversed()
                .thenComparing(StoreDto.AdminScoreRequest::lastAt, Comparator.reverseOrder()))
            .toList();
    }

    @Transactional
    public List<StoreDto.AdminScoreRequest> setStatus(Long gameRequestId, String status) {
        ScoreSheetGameRequest game = gameRequestRepository.findById(gameRequestId)
            .orElseThrow(() -> new StoreException(StoreErrorCode.REQUEST_NOT_FOUND));
        try {
            game.setStatus(ScoreSheetGameRequest.Status.valueOf(status));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new StoreException(StoreErrorCode.INVALID_INPUT);
        }
        return adminList();
    }

    /** 게임 점수판을 만들면(6d) 같은 이름 요청을 추가됨으로 */
    @Transactional
    public void markAdded(String gameName) {
        if (gameName == null) return;
        gameRequestRepository.findByNormalizedName(ScoreSheetGameRequest.normalize(gameName))
            .ifPresent(g -> g.setStatus(ScoreSheetGameRequest.Status.ADDED));
    }

    private static Map<Long, Set<Long>> people(List<ScoreSheetRequestEntry> entries) {
        return entries.stream().filter(e -> e.getMemberId() != null)
            .collect(Collectors.groupingBy(ScoreSheetRequestEntry::getGameRequestId,
                Collectors.mapping(ScoreSheetRequestEntry::getMemberId, Collectors.toSet())));
    }

    private static String storeLabel(Store s) {
        return s.getBranch() != null && !s.getBranch().isBlank() ? s.getBranch() : s.getName();
    }

    private static String clip(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty()) return null;
        return t.length() > TEXT_MAX ? t.substring(0, TEXT_MAX) : t;
    }
}
