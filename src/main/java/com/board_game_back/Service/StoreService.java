package com.board_game_back.Service;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreMember;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.StoreMemberRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Repository.StoreMatchParticipantRepository;
import com.board_game_back.Repository.StoreRatingRepository;
import com.board_game_back.Repository.StoreRoomRepository;
import com.board_game_back.Service.StoreAccessService.ViewerRole;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 손님 쪽 매장 기능: QR 진입·가입·체크인·나가기·목록·매장 홈. 기획: plan-store §2 1·2번 화면. */
@Service
@RequiredArgsConstructor
public class StoreService {

    /** 1b 가입 동의 문구 버전. 문구를 바꾸면 올리고 프론트 constants/store.js도 같이 올린다 */
    public static final int CONSENT_VERSION = 1;

    private final StoreRepository storeRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreRoomRepository storeRoomRepository;
    private final StoreRatingRepository storeRatingRepository;
    private final StoreMatchParticipantRepository storeMatchParticipantRepository;
    private final StoreAccessService storeAccessService;
    private final StoreQrService storeQrService;
    private final StoreScanTicketService storeScanTicketService;
    private final StorePartyService storePartyService;
    private final StoreRankingService storeRankingService;

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    // ── QR 진입 ──

    public StoreDto.ScanResponse scan(Long storeId, String token) {
        return scan(storeId, token, now());
    }

    StoreDto.ScanResponse scan(Long storeId, String token, LocalDateTime now) {
        Store store = getStore(storeId);
        StoreDto.StoreBrief brief = new StoreDto.StoreBrief(store.getId(), store.getName(), store.getBranch());
        if (store.isClosed()) return new StoreDto.ScanResponse("STORE_CLOSED", null, brief);
        if (!storeQrService.verify(store, token, now)) return new StoreDto.ScanResponse("EXPIRED_QR", null, brief);
        return new StoreDto.ScanResponse("VALID", storeScanTicketService.issue(storeId, now), brief);
    }

    @Transactional
    public StoreDto.CheckinResponse join(Long storeId, Long memberId, StoreDto.JoinRequest request) {
        return join(storeId, memberId, request, now());
    }

    @Transactional
    StoreDto.CheckinResponse join(Long storeId, Long memberId, StoreDto.JoinRequest request, LocalDateTime now) {
        Store store = getOpenStore(storeId);
        if (storeAccessService.isStaff(memberId)) throw new StoreException(StoreErrorCode.STAFF_CANNOT_JOIN);
        LocalDateTime scannedAt = storeScanTicketService.verify(request.ticket(), storeId, now);

        StoreMember member = storeMemberRepository.findByStoreIdAndMemberId(storeId, memberId).orElse(null);
        if (member == null) {
            if (request.consentVersion() < CONSENT_VERSION) throw new StoreException(StoreErrorCode.CONSENT_OUTDATED);
            member = new StoreMember(store.getId(), memberId, request.consentVersion(), now);
        }
        member.checkIn(scannedAt);
        storeMemberRepository.save(member);
        return checkinResponse(member, now);
    }

    @Transactional
    public StoreDto.CheckinResponse checkIn(Long storeId, Long memberId, String ticket) {
        return checkIn(storeId, memberId, ticket, now());
    }

    @Transactional
    StoreDto.CheckinResponse checkIn(Long storeId, Long memberId, String ticket, LocalDateTime now) {
        getOpenStore(storeId);
        if (storeAccessService.isStaff(memberId)) throw new StoreException(StoreErrorCode.STAFF_CANNOT_JOIN);
        LocalDateTime scannedAt = storeScanTicketService.verify(ticket, storeId, now);
        StoreMember member = storeMemberRepository.findByStoreIdAndMemberId(storeId, memberId)
            .orElseThrow(() -> new StoreException(StoreErrorCode.NOT_MEMBER));
        member.checkIn(scannedAt);
        return checkinResponse(member, now);
    }

    @Transactional
    public void leave(Long storeId, Long memberId) {
        getStore(storeId);
        if (storeAccessService.viewerRole(storeId, memberId) == ViewerRole.STAFF) {
            throw new StoreException(StoreErrorCode.STAFF_CANNOT_LEAVE);
        }
        StoreMember member = storeMemberRepository.findByStoreIdAndMemberId(storeId, memberId)
            .orElseThrow(() -> new StoreException(StoreErrorCode.NOT_MEMBER));
        // 판이 열려 있으면 막고(MATCH_OPEN), 그룹에서도 빠진다 (1c)
        storePartyService.leaveStore(storeId, memberId);
        storeRatingRepository.deleteByStoreIdAndMemberId(storeId, memberId);
        storeMatchParticipantRepository.anonymizeMemberInStore(storeId, memberId);
        storeMemberRepository.delete(member);
    }

    public StoreDto.CheckinQrResponse checkinQr(Long storeId, Long memberId) {
        LocalDateTime now = now();
        Store store = getOpenStore(storeId);
        storeAccessService.requireStaffOf(storeId, memberId);
        StoreQrService.CurrentQr qr = storeQrService.currentQr(store, now);
        return new StoreDto.CheckinQrResponse(qr.url(), StoreDto.iso(qr.expiresAt()), StoreDto.iso(qr.serverNow()));
    }

    // ── 목록 ──

    /** 입점 매장 (운영 중만). q는 이름·지점 부분 일치 */
    public List<StoreDto.DirectoryItem> directory(String q, Long memberId) {
        List<Store> stores = storeRepository.findByClosedAtIsNull();
        if (q != null && !q.isBlank()) {
            String needle = q.trim().toLowerCase();
            stores = stores.stream()
                .filter(s -> s.getName().toLowerCase().contains(needle)
                    || (s.getBranch() != null && s.getBranch().toLowerCase().contains(needle)))
                .toList();
        }
        Map<Long, Long> memberCounts = stores.isEmpty() ? Map.of() : countMap(storeMemberRepository.countByStoreIds(ids(stores)));
        Map<Long, Long> gameCounts = stores.isEmpty() ? Map.of() : countMap(storeRoomRepository.countOpenByStoreIds(ids(stores)));
        Set<Long> joined = storeMemberRepository.findByMemberIdOrderByJoinedAtAsc(memberId).stream()
            .map(StoreMember::getStoreId)
            .collect(Collectors.toSet());
        return stores.stream()
            .sorted(Comparator.comparing(Store::getRegion).thenComparing(Store::getName)
                .thenComparing(s -> s.getBranch() == null ? "" : s.getBranch()))
            .map(s -> new StoreDto.DirectoryItem(s.getId(), s.getName(), s.getBranch(), s.getRegion(),
                s.getAddress(), s.getImageUrl(), memberCounts.getOrDefault(s.getId(), 0L),
                gameCounts.getOrDefault(s.getId(), 0L), joined.contains(s.getId())))
            .toList();
    }

    /** 내 매장. STAFF는 지정된 매장 하나만 (plan-store §6.2) */
    public List<StoreDto.MyStoreItem> mine(Long memberId) {
        List<Long> storeIds;
        String role;
        if (storeAccessService.isStaff(memberId)) {
            storeIds = storeAccessService.staffStoreId(memberId).map(List::of).orElse(List.of());
            role = ViewerRole.STAFF.name();
        } else {
            storeIds = storeMemberRepository.findByMemberIdOrderByJoinedAtAsc(memberId).stream()
                .map(StoreMember::getStoreId)
                .toList();
            role = ViewerRole.MEMBER.name();
        }
        Map<Long, Store> stores = storeRepository.findAllById(storeIds).stream()
            .collect(Collectors.toMap(Store::getId, Function.identity()));
        Map<Long, Long> memberCounts = storeIds.isEmpty() ? Map.of() : countMap(storeMemberRepository.countByStoreIds(storeIds));
        Map<Long, Long> gameCounts = storeIds.isEmpty() ? Map.of() : countMap(storeRoomRepository.countOpenByStoreIds(storeIds));

        List<StoreDto.MyStoreItem> result = new ArrayList<>();
        for (Long id : storeIds) {
            Store s = stores.get(id);
            if (s == null) continue;
            result.add(new StoreDto.MyStoreItem(s.getId(), s.getName(), s.getBranch(), s.getImageUrl(),
                memberCounts.getOrDefault(id, 0L), gameCounts.getOrDefault(id, 0L), role, s.isClosed(),
                StoreDto.iso(s.getClosedAt())));
        }
        return result;
    }

    // ── 매장 홈 ──

    public StoreDto.HomeResponse home(Long storeId, Long memberId) {
        LocalDateTime now = now();
        Store store = getStore(storeId);
        ViewerRole role = storeAccessService.viewerRole(storeId, memberId);

        StoreDto.Checkin checkin = null;
        if (role == ViewerRole.MEMBER) {
            StoreMember member = storeMemberRepository.findByStoreIdAndMemberId(storeId, memberId).orElseThrow();
            checkin = new StoreDto.Checkin(member.isCheckedIn(now), StoreDto.iso(member.checkinExpiresAt()));
        }

        List<StoreDto.GameItem> gameItems = storeRankingService.visibleGames(storeId);
        List<StoreDto.GameRanking> rankings = storeRankingService.homeRankings(storeId, gameItems, memberId);

        StoreDto.PartyBrief party = storePartyService.membership(memberId)
            .flatMap(pm -> storePartyService.findParty(pm.getPartyId()))
            .filter(p -> p.isActive() && p.getStoreId().equals(storeId))
            .map(storePartyService::brief)
            .orElse(null);

        return new StoreDto.HomeResponse(
            info(store),
            new StoreDto.Viewer(role.name(), checkin),
            storeMemberRepository.countByStoreId(storeId),
            gameItems,
            party,
            storeRankingService.mvp(storeId, rankings),
            rankings);
    }

    // ── 공통 ──

    static StoreDto.StoreInfo info(Store s) {
        return new StoreDto.StoreInfo(s.getId(), s.getName(), s.getBranch(), s.getAddress(), s.getRegion(),
            s.getPhone(), s.getImageUrl(), StoreDto.hhmm(s.getOpenTime()), StoreDto.hhmm(s.getCloseTime()), s.isClosed());
    }

    private Store getStore(Long storeId) {
        return storeRepository.findById(storeId).orElseThrow(() -> new StoreException(StoreErrorCode.STORE_NOT_FOUND));
    }

    private Store getOpenStore(Long storeId) {
        Store store = getStore(storeId);
        if (store.isClosed()) throw new StoreException(StoreErrorCode.STORE_CLOSED);
        return store;
    }

    private static StoreDto.CheckinResponse checkinResponse(StoreMember member, LocalDateTime now) {
        return new StoreDto.CheckinResponse(member.isCheckedIn(now), StoreDto.iso(member.checkinExpiresAt()));
    }

    private static List<Long> ids(Collection<Store> stores) {
        return stores.stream().map(Store::getId).toList();
    }

    private static Map<Long, Long> countMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) map.put((Long) row[0], (Long) row[1]);
        return map;
    }
}
