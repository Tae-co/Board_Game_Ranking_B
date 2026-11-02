package com.board_game_back.Service;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreRoom;
import com.board_game_back.Entity.StoreStaff;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.StoreMemberRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Repository.StoreRoomRepository;
import com.board_game_back.Repository.StoreStaffRepository;
import com.board_game_back.Utils.RegionTimeZones;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 어드민 매장 탭: 매장 등록·수정·운영 끝내기·STAFF 지정·게임방 일괄 생성 (plan-store §2.2). */
@Service
@RequiredArgsConstructor
public class StoreAdminService {

    private final StoreRepository storeRepository;
    private final StoreStaffRepository storeStaffRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreRoomRepository storeRoomRepository;
    private final BoardGameRepository boardGameRepository;
    private final MemberRepository memberRepository;
    private final StoreAccessService storeAccessService;

    public List<StoreDto.AdminStoreItem> list() {
        List<Store> stores = storeRepository.findAll();
        Map<Long, Long> memberCounts = new HashMap<>();
        if (!stores.isEmpty()) {
            for (Object[] row : storeMemberRepository.countByStoreIds(stores.stream().map(Store::getId).toList())) {
                memberCounts.put((Long) row[0], (Long) row[1]);
            }
        }
        return stores.stream()
            .sorted(Comparator.comparing(Store::getId))
            .map(s -> new StoreDto.AdminStoreItem(StoreService.info(s), memberCounts.getOrDefault(s.getId(), 0L),
                staff(s.getId()), StoreDto.iso(s.getCreatedAt()), StoreDto.iso(s.getClosedAt())))
            .toList();
    }

    @Transactional
    public StoreDto.AdminStoreItem create(StoreDto.StoreForm form) {
        Form f = validate(form);
        Store store = storeRepository.save(Store.builder()
            .name(f.name).branch(f.branch).address(f.address).region(f.region).phone(f.phone).imageUrl(f.imageUrl)
            .openTime(f.openTime).closeTime(f.closeTime)
            .qrSecret(StoreQrService.newSecret())
            .build());
        for (BoardGame game : storeGames()) {
            storeRoomRepository.save(new StoreRoom(store.getId(), game.getId()));
        }
        return item(store);
    }

    /** 영업시간도 저장하는 즉시 적용한다 (24차) */
    @Transactional
    public StoreDto.AdminStoreItem update(Long storeId, StoreDto.StoreForm form) {
        Form f = validate(form);
        Store store = getStore(storeId);
        store.update(f.name, f.branch, f.address, f.region, f.phone, f.imageUrl, f.openTime, f.closeTime);
        return item(store);
    }

    /** 매장 운영 끝내기. 기록은 남기고 QR·가입·체크인을 막는다 */
    @Transactional
    public StoreDto.AdminStoreItem close(Long storeId) {
        Store store = getStore(storeId);
        store.close(LocalDateTime.now(ZoneOffset.UTC));
        return item(store);
    }

    // ── STAFF ──

    public List<StoreDto.StaffInfo> staff(Long storeId) {
        Map<Long, LocalDateTime> assignedAt = storeStaffRepository.findByStoreId(storeId).stream()
            .collect(Collectors.toMap(StoreStaff::getMemberId, StoreStaff::getAssignedAt));
        return memberRepository.findAllById(assignedAt.keySet()).stream()
            .sorted(Comparator.comparing(m -> assignedAt.get(m.getId())))
            .map(m -> new StoreDto.StaffInfo(m.getId(), m.getNickname(), StoreDto.iso(assignedAt.get(m.getId()))))
            .toList();
    }

    /** STAFF 역할이 있고 어느 매장에도 지정되지 않은 계정 */
    public List<StoreDto.StaffInfo> staffCandidates() {
        Set<Long> assigned = new HashSet<>(storeStaffRepository.findAllMemberIds());
        return memberRepository.findByRole(StoreAccessService.STAFF_ROLE).stream()
            .filter(m -> !assigned.contains(m.getId()))
            .sorted(Comparator.comparing(Member::getId))
            .map(m -> new StoreDto.StaffInfo(m.getId(), m.getNickname(), null))
            .toList();
    }

    @Transactional
    public List<StoreDto.StaffInfo> assignStaff(Long storeId, Long memberId) {
        getStore(storeId);
        if (!storeAccessService.isStaff(memberId)) throw new StoreException(StoreErrorCode.NOT_STAFF_ROLE);
        StoreStaff existing = storeStaffRepository.findByMemberId(memberId).orElse(null);
        if (existing != null) {
            if (!existing.getStoreId().equals(storeId)) throw new StoreException(StoreErrorCode.STAFF_ALREADY_ASSIGNED);
            return staff(storeId);
        }
        storeStaffRepository.save(new StoreStaff(storeId, memberId));
        return staff(storeId);
    }

    @Transactional
    public List<StoreDto.StaffInfo> unassignStaff(Long storeId, Long memberId) {
        storeStaffRepository.findByMemberId(memberId)
            .filter(s -> s.getStoreId().equals(storeId))
            .ifPresent(storeStaffRepository::delete);
        return staff(storeId);
    }

    // ── 게임방 ──

    /** 공식 게임을 추가하면 모든 매장에 게임방이 생긴다 (plan-store §1 대상 게임) */
    @Transactional
    public void createRoomsForGame(BoardGame game) {
        if (!isStoreGame(game)) return;
        for (Store store : storeRepository.findAll()) {
            storeRoomRepository.save(new StoreRoom(store.getId(), game.getId()));
        }
    }

    @Transactional
    public void deleteRoomsForGame(Long boardGameId) {
        storeRoomRepository.deleteByBoardGameId(boardGameId);
    }

    /** 매장 게임방 대상 = 공식(커뮤니티 전용 아님) ELO 게임. 칩 게임은 순위가 없어 뺀다 */
    static boolean isStoreGame(BoardGame game) {
        return game.getCommunityId() == null && !game.isChipGame();
    }

    private List<BoardGame> storeGames() {
        return boardGameRepository.findByCommunityIdIsNull().stream().filter(StoreAdminService::isStoreGame).toList();
    }

    // ── 공통 ──

    private StoreDto.AdminStoreItem item(Store store) {
        return new StoreDto.AdminStoreItem(StoreService.info(store), storeMemberRepository.countByStoreId(store.getId()),
            staff(store.getId()), StoreDto.iso(store.getCreatedAt()), StoreDto.iso(store.getClosedAt()));
    }

    private Store getStore(Long storeId) {
        return storeRepository.findById(storeId).orElseThrow(() -> new StoreException(StoreErrorCode.STORE_NOT_FOUND));
    }

    private record Form(String name, String branch, String address, String region, String phone, String imageUrl,
                        LocalTime openTime, LocalTime closeTime) {}

    static Form validate(StoreDto.StoreForm form) {
        String name = trim(form.name());
        String address = trim(form.address());
        if (name == null || name.length() > 60 || address == null || address.length() > 200) {
            throw new StoreException(StoreErrorCode.INVALID_INPUT);
        }
        String branch = trim(form.branch());
        String phone = trim(form.phone());
        if ((branch != null && branch.length() > 60) || (phone != null && phone.length() > 30)) {
            throw new StoreException(StoreErrorCode.INVALID_INPUT);
        }
        if (!RegionTimeZones.isKnown(form.region())) throw new StoreException(StoreErrorCode.INVALID_REGION);

        LocalTime open = parseTime(form.openTime());
        LocalTime close = parseTime(form.closeTime());
        if (open.equals(close)) throw new StoreException(StoreErrorCode.INVALID_HOURS);
        return new Form(name, branch, address, form.region(), phone, trim(form.imageUrl()), open, close);
    }

    private static LocalTime parseTime(String value) {
        if (value == null) throw new StoreException(StoreErrorCode.INVALID_HOURS);
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new StoreException(StoreErrorCode.INVALID_HOURS);
        }
    }

    private static String trim(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
