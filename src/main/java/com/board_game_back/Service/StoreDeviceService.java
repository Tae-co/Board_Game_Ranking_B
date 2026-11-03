package com.board_game_back.Service;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreContest;
import com.board_game_back.Entity.StoreDevice;
import com.board_game_back.Entity.StoreMatch;
import com.board_game_back.Entity.StoreMatchParticipant;
import com.board_game_back.Entity.StorePairCode;
import com.board_game_back.Entity.StoreRating;
import com.board_game_back.Entity.StoreTvSetting;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.StoreContestRepository;
import com.board_game_back.Repository.StoreDeviceRepository;
import com.board_game_back.Repository.StoreMatchParticipantRepository;
import com.board_game_back.Repository.StoreMatchRepository;
import com.board_game_back.Repository.StorePairCodeRepository;
import com.board_game_back.Repository.StoreRatingRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Repository.StoreTvSettingRepository;
import com.board_game_back.Utils.SlidingWindowLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매장 기기 등록·기기 화면·TV 설정 (plan-store §2 2l·4·4f·4j·6g·6k, §6.6).
 *
 * <p>등록: 기기가 6자리를 받고(4j) → STAFF 폰에 입력(2l) → 기기가 자기만 아는 nonce로 폴링해 토큰을 받아 간다.
 * 토큰은 해시만 저장하고 요청마다 DB로 확인한다 — 해제(2l·6g)하면 다음 요청부터 바로 끊긴다.
 */
@Service
@RequiredArgsConstructor
public class StoreDeviceService {

    public static final int MAX_DEVICES = 30;
    public static final Duration PAIR_TTL = Duration.ofMinutes(10);
    /** 4e 방금 끝난 경기: 기기는 30초마다 부르니 그보다 넉넉하게 */
    public static final Duration JUST_FINISHED_WINDOW = Duration.ofMinutes(2);
    public static final int TOP10 = 10;
    private static final Duration SEEN_THROTTLE = Duration.ofMinutes(1);
    private static final int NAME_MAX = 30;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 4j 숫자 받기: IP당 1분 10번 (비로그인) */
    private final SlidingWindowLimiter pairCodeLimiter = new SlidingWindowLimiter(10, 60_000);
    /** 2l 숫자 입력: STAFF당 1분 5번 — 6자리 맞히기 방지 */
    private final SlidingWindowLimiter registerLimiter = new SlidingWindowLimiter(5, 60_000);

    private final StoreRepository storeRepository;
    private final StoreDeviceRepository deviceRepository;
    private final StorePairCodeRepository pairCodeRepository;
    private final StoreTvSettingRepository tvSettingRepository;
    private final StoreRatingRepository storeRatingRepository;
    private final StoreMatchRepository storeMatchRepository;
    private final StoreMatchParticipantRepository participantRepository;
    private final BoardGameRepository boardGameRepository;
    private final MemberRepository memberRepository;
    private final StoreAccessService storeAccessService;
    private final StoreQrService storeQrService;
    private final StoreRankingService storeRankingService;
    private final StorePartyService storePartyService;
    private final StoreSeasonService storeSeasonService;
    private final StoreContestService storeContestService;
    private final StoreEventService storeEventService;
    private final StoreContestRepository storeContestRepository;

    static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    // ── 4j 기기 쪽 등록 ──

    @Transactional
    public StoreDto.PairCodeResponse newPairCode(String ip, LocalDateTime now) {
        if (!pairCodeLimiter.tryAcquire(String.valueOf(ip), System.currentTimeMillis())) {
            throw new StoreException(StoreErrorCode.TOO_MANY_REQUESTS);
        }
        String code = null;
        for (int i = 0; i < 20 && code == null; i++) {
            String candidate = String.format("%06d", RANDOM.nextInt(1_000_000));
            if (pairCodeRepository.findLive(candidate, now).isEmpty()) code = candidate;
        }
        if (code == null) throw new StoreException(StoreErrorCode.TOO_MANY_REQUESTS);
        StorePairCode pair = pairCodeRepository.save(new StorePairCode(code, randomHex(32), now, now.plus(PAIR_TTL)));
        return new StoreDto.PairCodeResponse(pair.getCode(), pair.getNonce(), StoreDto.iso(pair.getExpiresAt()),
            StoreDto.iso(now));
    }

    /** 기기 폴링. STAFF가 입력했으면 그때 토큰을 만들어 한 번만 돌려주고 숫자 줄은 지운다 */
    @Transactional
    public StoreDto.PairStatusResponse pairStatus(String nonce, LocalDateTime now) {
        StorePairCode pair = pairCodeRepository.findByNonce(nonce).orElse(null);
        if (pair == null) return new StoreDto.PairStatusResponse("EXPIRED", null, null);
        if (pair.getClaimedDeviceId() != null) {
            StoreDevice device = deviceRepository.findById(pair.getClaimedDeviceId()).orElse(null);
            pairCodeRepository.delete(pair);
            if (device == null || !device.isActive() || device.getTokenHash() != null) {
                return new StoreDto.PairStatusResponse("EXPIRED", null, null);
            }
            String token = randomToken();
            device.issueToken(hash(token));
            return new StoreDto.PairStatusResponse("CLAIMED", token, device.getKind().name());
        }
        if (pair.isExpired(now)) {
            pairCodeRepository.delete(pair);
            return new StoreDto.PairStatusResponse("EXPIRED", null, null);
        }
        return new StoreDto.PairStatusResponse("WAITING", null, null);
    }

    // ── 2l STAFF 폰 ──

    @Transactional
    public List<StoreDto.DeviceItem> register(Long storeId, Long memberId, StoreDto.RegisterDeviceRequest request,
                                              LocalDateTime now) {
        Store store = getOpenStore(storeId);
        storeAccessService.requireStaffOf(storeId, memberId);
        if (!registerLimiter.tryAcquire(String.valueOf(memberId), System.currentTimeMillis())) {
            throw new StoreException(StoreErrorCode.TOO_MANY_REQUESTS);
        }
        StoreDevice.Kind kind = parseKind(request == null ? null : request.kind());
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > NAME_MAX) throw new StoreException(StoreErrorCode.INVALID_INPUT);
        if (deviceRepository.countByStoreIdAndRevokedAtIsNull(storeId) >= MAX_DEVICES) {
            throw new StoreException(StoreErrorCode.DEVICE_LIMIT);
        }
        String code = request.code() == null ? "" : request.code().replaceAll("\\s", "");
        StorePairCode pair = pairCodeRepository.findLive(code, now).stream().findFirst()
            .orElseThrow(() -> new StoreException(StoreErrorCode.PAIR_CODE_INVALID));
        StoreDevice device = deviceRepository.save(new StoreDevice(store.getId(), kind, name, now));
        pair.claim(device.getId());
        return items(storeId);
    }

    public List<StoreDto.DeviceItem> devices(Long storeId, Long memberId) {
        getStore(storeId);
        storeAccessService.requireStaffOf(storeId, memberId);
        return items(storeId);
    }

    @Transactional
    public List<StoreDto.DeviceItem> revoke(Long storeId, Long deviceId, Long memberId) {
        storeAccessService.requireStaffOf(storeId, memberId);
        return revoke(storeId, deviceId);
    }

    // ── 6g 어드민 ──

    public List<StoreDto.DeviceItem> adminDevices(Long storeId) {
        getStore(storeId);
        return items(storeId);
    }

    @Transactional
    public List<StoreDto.DeviceItem> revoke(Long storeId, Long deviceId) {
        StoreDevice device = deviceRepository.findById(deviceId)
            .filter(d -> d.getStoreId().equals(storeId) && d.isActive())
            .orElseThrow(() -> new StoreException(StoreErrorCode.DEVICE_NOT_FOUND));
        device.revoke(now());
        return items(storeId);
    }

    /** 6g QR 비밀키 새로 만들기 */
    @Transactional
    public void rotateQrSecret(Long storeId) {
        getStore(storeId).rotateQrSecret(StoreQrService.newSecret());
    }

    // ── 기기 화면 (4·4f) ──

    @Transactional
    public StoreDto.DisplayResponse display(String rawToken, String ip, LocalDateTime now) {
        StoreDevice device = authenticate(rawToken);
        Store store = getStore(device.getStoreId());
        if (device.getLastSeenAt() == null || !Objects.equals(ip, device.getLastIp())
            || Duration.between(device.getLastSeenAt(), now).compareTo(SEEN_THROTTLE) >= 0) {
            device.seen(ip, now);
        }
        StoreQrService.CurrentQr qr = storeQrService.currentQr(store, now);
        StoreDto.DeviceBrief brief = new StoreDto.DeviceBrief(device.getId(), device.getKind().name(), device.getName());
        StoreDto.StoreBrief storeBrief = new StoreDto.StoreBrief(store.getId(), store.getName(), store.getBranch());
        StoreDto.QrInfo qrInfo = new StoreDto.QrInfo(qr.url(), StoreDto.iso(qr.expiresAt()));
        if (device.getKind() == StoreDevice.Kind.TABLE) {
            return new StoreDto.DisplayResponse(brief, storeBrief, StoreDto.iso(now), qrInfo, null, null, null, null, null, null,
                null, null, null);
        }
        StoreTvSetting setting = setting(store.getId(), now);
        List<StoreDto.GameItem> games = storeRankingService.visibleGames(store.getId());
        return new StoreDto.DisplayResponse(brief, storeBrief, StoreDto.iso(now), qrInfo, settingDto(setting),
            storeRankingService.mvp(store.getId(), storeRankingService.homeRankings(store.getId(), games, null)),
            top10(store.getId(), games, setting.hiddenGameIds()),
            setting.isShowJustFinished() ? justFinished(store.getId(), now) : List.of(),
            storeSeasonService.currentInfo(store.getId()), storeSeasonService.recentResult(store.getId(), now),
            storeEventService.activeCards(store), storeContestService.recruitingCards(store),
            storeContestService.board(store, now));
    }

    StoreDevice authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new StoreException(StoreErrorCode.DEVICE_TOKEN_INVALID);
        StoreDevice device = deviceRepository.findByTokenHash(hash(rawToken))
            .filter(StoreDevice::isActive)
            .orElseThrow(() -> new StoreException(StoreErrorCode.DEVICE_TOKEN_INVALID));
        if (getStore(device.getStoreId()).isClosed()) throw new StoreException(StoreErrorCode.DEVICE_TOKEN_INVALID);
        return device;
    }

    // ── 6k TV 화면 설정 ──

    public StoreDto.TvSettingResponse tvSetting(Long storeId) {
        LocalDateTime now = now();
        Store store = getStore(storeId);
        StoreTvSetting setting = setting(storeId, now);
        List<StoreDto.GameItem> games = storeRankingService.visibleGames(storeId);
        Map<Long, Long> matchCounts = new HashMap<>();
        for (Object[] row : storeMatchRepository.countRecordedByRoom(storeId)) matchCounts.put((Long) row[0], (Long) row[1]);
        List<StoreDevice> tvs = deviceRepository.findByStoreIdAndRevokedAtIsNullOrderByIdAsc(storeId).stream()
            .filter(d -> d.getKind() == StoreDevice.Kind.TV).toList();
        LocalDateTime lastSeen = tvs.stream().map(StoreDevice::getLastSeenAt).filter(Objects::nonNull)
            .max(Comparator.naturalOrder()).orElse(null);
        return new StoreDto.TvSettingResponse(settingDto(setting),
            games.stream().map(g -> new StoreDto.TvGame(g.boardGameId(), g.name(), matchCounts.getOrDefault(g.storeRoomId(), 0L))).toList(),
            tvs.size(), StoreDto.iso(lastSeen),
            storeRankingService.mvp(storeId, storeRankingService.homeRankings(storeId, games, null)),
            top10(storeId, games, List.of()), storeSeasonService.recentResult(storeId, now),
            storeEventService.activeCards(store), storeContestService.recruitingCards(store), storeContestService.board(store, now));
    }

    @Transactional
    public StoreDto.TvSettingResponse saveTvSetting(Long storeId, StoreDto.TvSetting form) {
        LocalDateTime now = now();
        getStore(storeId);
        List<StoreTvSetting.Screen> order = parseOrder(form);
        if (!StoreTvSetting.INTERVALS.contains(form.intervalSec())) throw new StoreException(StoreErrorCode.INVALID_TV_SETTING);
        List<Long> hidden = form.top10HiddenGameIds() == null ? List.of()
            : form.top10HiddenGameIds().stream().filter(Objects::nonNull).distinct().toList();
        StoreTvSetting setting = tvSettingRepository.findById(storeId).orElseGet(() -> StoreTvSetting.defaults(storeId, now));
        setting.update(order, form.intervalSec(), form.showJustFinished(), hidden, now);
        tvSettingRepository.save(setting);
        return tvSetting(storeId);
    }

    static List<StoreTvSetting.Screen> parseOrder(StoreDto.TvSetting form) {
        if (form == null || form.order() == null) throw new StoreException(StoreErrorCode.INVALID_TV_SETTING);
        List<StoreTvSetting.Screen> order = new ArrayList<>();
        try {
            for (String s : form.order()) order.add(StoreTvSetting.Screen.valueOf(s));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new StoreException(StoreErrorCode.INVALID_TV_SETTING);
        }
        // 체크인 QR 크게(4h)는 TV에서 QR이 뜨는 유일한 화면이라 뺄 수 없다 (41차)
        if (new HashSet<>(order).size() != order.size() || !order.contains(StoreTvSetting.Screen.QR)) {
            throw new StoreException(StoreErrorCode.INVALID_TV_SETTING);
        }
        return order;
    }

    // ── 정리 ──

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    @Transactional
    public void cleanupPairCodes() {
        pairCodeRepository.deleteExpiredBefore(now().minusDays(1));
    }

    // ── 공통 ──

    private StoreTvSetting setting(Long storeId, LocalDateTime now) {
        return tvSettingRepository.findById(storeId).orElseGet(() -> StoreTvSetting.defaults(storeId, now));
    }

    private static StoreDto.TvSetting settingDto(StoreTvSetting s) {
        return new StoreDto.TvSetting(s.order().stream().map(Enum::name).toList(), s.getIntervalSec(),
            s.isShowJustFinished(), s.hiddenGameIds());
    }

    /** 4i: 고른 게임 중 기록이 있는 게임만 */
    private List<StoreDto.Top10> top10(Long storeId, List<StoreDto.GameItem> games, List<Long> hidden) {
        List<StoreDto.GameItem> shown = games.stream().filter(g -> !hidden.contains(g.boardGameId())).toList();
        if (shown.isEmpty()) return List.of();
        Map<Long, List<StoreRating>> byGame = storeRatingRepository
            .findByStoreIdAndBoardGameIdIn(storeId, shown.stream().map(StoreDto.GameItem::boardGameId).toList())
            .stream().collect(Collectors.groupingBy(StoreRating::getBoardGameId));
        Set<Long> memberIds = byGame.values().stream().flatMap(List::stream).map(StoreRating::getMemberId).collect(Collectors.toSet());
        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
            .collect(Collectors.toMap(Member::getId, Function.identity()));
        List<StoreDto.Top10> result = new ArrayList<>();
        for (StoreDto.GameItem g : shown) {
            List<StoreDto.RankItem> ranked = storeRankingService.rank(byGame.getOrDefault(g.boardGameId(), List.of()), members, null);
            if (ranked.isEmpty()) continue;
            result.add(new StoreDto.Top10(g.boardGameId(), g.name(), ranked.subList(0, Math.min(TOP10, ranked.size()))));
        }
        return result;
    }

    private List<StoreDto.JustFinished> justFinished(Long storeId, LocalDateTime now) {
        List<StoreMatch> matches = storeMatchRepository.findByStoreIdAndStatusAndEndedAtAfterOrderByEndedAtAsc(
            storeId, StoreMatch.Status.RECORDED, now.minus(JUST_FINISHED_WINDOW));
        if (matches.isEmpty()) return List.of();
        Map<Long, List<StoreMatchParticipant>> byMatch = participantRepository
            .findByStoreMatchIdIn(matches.stream().map(StoreMatch::getId).toList()).stream()
            .collect(Collectors.groupingBy(StoreMatchParticipant::getStoreMatchId));
        Map<Long, String> names = storePartyService.nicknames(byMatch.values().stream().flatMap(List::stream)
            .map(StoreMatchParticipant::getMemberId).filter(Objects::nonNull).toList());
        Map<Long, String> gameNames = boardGameRepository.findByIdIn(matches.stream().map(StoreMatch::getBoardGameId).toList())
            .stream().collect(Collectors.toMap(BoardGame::getId, BoardGame::getName));
        Map<Long, String> contestTitles = storeContestRepository.findAllById(
                matches.stream().map(StoreMatch::getContestId).filter(Objects::nonNull).distinct().toList())
            .stream().collect(Collectors.toMap(StoreContest::getId, StoreContest::getTitle));
        return matches.stream()
            .map(m -> new StoreDto.JustFinished(m.getId(), gameNames.get(m.getBoardGameId()),
                StoreDto.iso(m.getStartedAt()), StoreDto.iso(m.getEndedAt()),
                byMatch.getOrDefault(m.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(StoreMatchParticipant::getPlacement, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(p -> StoreMatchService.participantItem(p, names, true))
                    .map(p -> new StoreDto.JustFinishedPlayer(p.placement(), p.nickname(), p.gameScore(), p.ratingChange()))
                    .toList(),
                m.isContest() ? contestTitles.get(m.getContestId()) : null, m.getContestRound()))
            .toList();
    }

    private List<StoreDto.DeviceItem> items(Long storeId) {
        return deviceRepository.findByStoreIdAndRevokedAtIsNullOrderByIdAsc(storeId).stream()
            .map(d -> new StoreDto.DeviceItem(d.getId(), d.getKind().name(), d.getName(), d.getTokenHash() != null,
                StoreDto.iso(d.getCreatedAt()), StoreDto.iso(d.getLastSeenAt()), d.getLastIp()))
            .toList();
    }

    private static StoreDevice.Kind parseKind(String kind) {
        try {
            return StoreDevice.Kind.valueOf(kind);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new StoreException(StoreErrorCode.INVALID_INPUT);
        }
    }

    private Store getStore(Long storeId) {
        return storeRepository.findById(storeId).orElseThrow(() -> new StoreException(StoreErrorCode.STORE_NOT_FOUND));
    }

    private Store getOpenStore(Long storeId) {
        Store store = getStore(storeId);
        if (store.isClosed()) throw new StoreException(StoreErrorCode.STORE_CLOSED);
        return store;
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
