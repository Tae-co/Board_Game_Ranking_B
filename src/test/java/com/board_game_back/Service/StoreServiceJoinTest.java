package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreMember;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.StoreMemberRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Repository.StoreRoomRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** QR 진입 → 가입 동의 → 체크인 (plan-store 1b·1f, 30·48차) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreServiceJoinTest {

    private static final Long STORE_ID = 3L;
    private static final Long MEMBER_ID = 16L;
    private static final LocalDateTime SCANNED = LocalDateTime.of(2026, 11, 2, 5, 0);

    @Mock private StoreRepository storeRepository;
    @Mock private StoreMemberRepository storeMemberRepository;
    @Mock private StoreRoomRepository storeRoomRepository;
    @Mock private BoardGameRepository boardGameRepository;
    @Mock private StoreAccessService storeAccessService;
    @Mock private com.board_game_back.Repository.StoreRatingRepository storeRatingRepository;
    @Mock private com.board_game_back.Repository.StoreMatchParticipantRepository storeMatchParticipantRepository;
    @Mock private StorePartyService storePartyService;
    @Mock private StoreRankingService storeRankingService;

    private final StoreQrService qr = new StoreQrService();
    private final StoreScanTicketService tickets = new StoreScanTicketService("test-jwt-secret");
    private StoreService service;
    private Store store;

    @BeforeEach
    void setUp() {
        service = new StoreService(storeRepository, storeMemberRepository, storeRoomRepository, storeRatingRepository,
            storeMatchParticipantRepository,
            storeAccessService, qr, tickets, storePartyService, storeRankingService);
        store = Store.builder().name("야다").address("서울").region("South Korea")
            .openTime(LocalTime.of(10, 0)).closeTime(LocalTime.of(22, 0)).qrSecret("secret").build();
        ReflectionTestUtils.setField(store, "id", STORE_ID);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(storeMemberRepository.findByStoreIdAndMemberId(STORE_ID, MEMBER_ID)).thenReturn(Optional.empty());
        when(storeMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private StoreDto.JoinRequest join(int consentVersion) {
        return new StoreDto.JoinRequest(tickets.issue(STORE_ID, SCANNED), consentVersion);
    }

    @Test
    void 지금_QR이면_티켓을_주고_지난_QR이면_EXPIRED_QR() {
        String url = qr.currentQr(store, SCANNED).url();
        String token = url.substring(url.indexOf("?t=") + 3);

        StoreDto.ScanResponse ok = service.scan(STORE_ID, token, SCANNED.plusMinutes(30));
        StoreDto.ScanResponse late = service.scan(STORE_ID, token, SCANNED.plusMinutes(60));

        assertThat(ok.result()).isEqualTo("VALID");
        assertThat(tickets.verify(ok.ticket(), STORE_ID, SCANNED.plusMinutes(31))).isEqualTo(SCANNED.plusMinutes(30));
        assertThat(late.result()).isEqualTo("EXPIRED_QR");
        assertThat(late.ticket()).isNull();
    }

    @Test
    void 운영_끝난_매장은_STORE_CLOSED() {
        store.close(SCANNED.minusDays(1));

        assertThat(service.scan(STORE_ID, "any", SCANNED).result()).isEqualTo("STORE_CLOSED");
        assertCode(() -> service.join(STORE_ID, MEMBER_ID, join(StoreService.CONSENT_VERSION), SCANNED),
            StoreErrorCode.STORE_CLOSED);
    }

    @Test
    void 가입하면_찍은_순간부터_3시간_체크인() {
        StoreDto.CheckinResponse res = service.join(STORE_ID, MEMBER_ID, join(StoreService.CONSENT_VERSION),
            SCANNED.plusHours(2).plusMinutes(59));

        ArgumentCaptor<StoreMember> saved = ArgumentCaptor.forClass(StoreMember.class);
        verify(storeMemberRepository).save(saved.capture());
        assertThat(saved.getValue().getConsentVersion()).isEqualTo(StoreService.CONSENT_VERSION);
        assertThat(saved.getValue().getCheckedInAt()).isEqualTo(SCANNED);
        assertThat(res.checkedIn()).isTrue();
        assertThat(res.checkinExpiresAt()).isEqualTo("2026-11-02T08:00:00Z");
    }

    @Test
    void 찍고_3시간_지나서_가입하면_멤버만_되고_체크인은_없다() {
        StoreDto.CheckinResponse res = service.join(STORE_ID, MEMBER_ID, join(StoreService.CONSENT_VERSION),
            SCANNED.plusHours(3).plusMinutes(1));

        verify(storeMemberRepository).save(any());
        assertThat(res.checkedIn()).isFalse();
    }

    @Test
    void 동의_버전이_낮으면_거부() {
        assertCode(() -> service.join(STORE_ID, MEMBER_ID, join(0), SCANNED), StoreErrorCode.CONSENT_OUTDATED);
        verify(storeMemberRepository, never()).save(any());
    }

    @Test
    void STAFF_역할은_가입할_수_없다() {
        when(storeAccessService.isStaff(MEMBER_ID)).thenReturn(true);

        assertCode(() -> service.join(STORE_ID, MEMBER_ID, join(StoreService.CONSENT_VERSION), SCANNED),
            StoreErrorCode.STAFF_CANNOT_JOIN);
    }

    @Test
    void 이미_멤버면_체크인만_갱신하고_동의_기록은_그대로() {
        StoreMember existing = new StoreMember(STORE_ID, MEMBER_ID, 1, SCANNED.minusDays(10));
        existing.checkIn(SCANNED.minusDays(10));
        when(storeMemberRepository.findByStoreIdAndMemberId(STORE_ID, MEMBER_ID)).thenReturn(Optional.of(existing));

        service.join(STORE_ID, MEMBER_ID, join(StoreService.CONSENT_VERSION), SCANNED.plusMinutes(1));

        assertThat(existing.getCheckedInAt()).isEqualTo(SCANNED);
        assertThat(existing.getConsentedAt()).isEqualTo(SCANNED.minusDays(10));
    }

    @Test
    void 옛_티켓으로_체크인을_되돌리지_않는다() {
        StoreMember existing = new StoreMember(STORE_ID, MEMBER_ID, 1, SCANNED.minusDays(1));
        existing.checkIn(SCANNED.plusHours(1));
        when(storeMemberRepository.findByStoreIdAndMemberId(STORE_ID, MEMBER_ID)).thenReturn(Optional.of(existing));

        service.checkIn(STORE_ID, MEMBER_ID, tickets.issue(STORE_ID, SCANNED), SCANNED.plusHours(1));

        assertThat(existing.getCheckedInAt()).isEqualTo(SCANNED.plusHours(1));
    }

    @Test
    void 멤버가_아니면_체크인_거부() {
        assertCode(() -> service.checkIn(STORE_ID, MEMBER_ID, tickets.issue(STORE_ID, SCANNED), SCANNED),
            StoreErrorCode.NOT_MEMBER);
    }

    @Test
    void 지정된_STAFF는_매장을_나갈_수_없다() {
        when(storeAccessService.viewerRole(STORE_ID, MEMBER_ID)).thenReturn(StoreAccessService.ViewerRole.STAFF);

        assertCode(() -> service.leave(STORE_ID, MEMBER_ID), StoreErrorCode.STAFF_CANNOT_LEAVE);
    }

    private static void assertCode(Runnable call, StoreErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(StoreException.class)
            .satisfies(e -> assertThat(((StoreException) e).code()).isEqualTo(code));
    }
}
