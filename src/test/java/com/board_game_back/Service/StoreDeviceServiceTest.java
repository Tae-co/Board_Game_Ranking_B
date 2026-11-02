package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreDevice;
import com.board_game_back.Entity.StorePairCode;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.StoreDeviceRepository;
import com.board_game_back.Repository.StorePairCodeRepository;
import com.board_game_back.Repository.StoreRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 매장 기기 등록(4j → 2l → 토큰)·해제·TV 설정 검사 (plan-store §6.6, 6k) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreDeviceServiceTest {

    private static final Long STORE_ID = 3L;
    private static final Long STAFF_ID = 16L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 6, 0);

    @Mock private StoreRepository storeRepository;
    @Mock private StoreDeviceRepository deviceRepository;
    @Mock private StorePairCodeRepository pairCodeRepository;
    @Mock private StoreAccessService storeAccessService;

    @InjectMocks private StoreDeviceService service;

    private Store store;

    @BeforeEach
    void setUp() {
        store = Store.builder().name("야다").address("서울").region("South Korea")
            .openTime(LocalTime.of(10, 0)).closeTime(LocalTime.of(22, 0)).qrSecret("s").build();
        ReflectionTestUtils.setField(store, "id", STORE_ID);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(deviceRepository.save(any())).thenAnswer(inv -> {
            StoreDevice d = inv.getArgument(0);
            ReflectionTestUtils.setField(d, "id", 100L);
            return d;
        });
        when(pairCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static StorePairCode pair(String code) {
        return new StorePairCode(code, "nonce-1", NOW, NOW.plusMinutes(10));
    }

    @Test
    void 숫자는_6자리이고_10분_뒤_만료된다() {
        StoreDto.PairCodeResponse r = service.newPairCode("1.2.3.4", NOW);
        assertThat(r.code()).matches("\\d{6}");
        assertThat(r.nonce()).hasSize(64);
        assertThat(r.expiresAt()).isEqualTo(StoreDto.iso(NOW.plusMinutes(10)));
    }

    @Test
    void 같은_IP가_1분에_10번_넘게_숫자를_받으면_막힌다() {
        for (int i = 0; i < 10; i++) service.newPairCode("9.9.9.9", NOW);
        assertThatThrownBy(() -> service.newPairCode("9.9.9.9", NOW))
            .isInstanceOf(StoreException.class)
            .extracting("code").isEqualTo(StoreErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void STAFF가_입력하면_기기가_폴링으로_토큰을_한_번만_받는다() {
        StorePairCode pair = pair("482913");
        when(pairCodeRepository.findLive("482913", NOW)).thenReturn(List.of(pair));
        service.register(STORE_ID, STAFF_ID, new StoreDto.RegisterDeviceRequest("482 913", "TV", "카운터 TV"), NOW);
        assertThat(pair.getClaimedDeviceId()).isEqualTo(100L);

        StoreDevice device = new StoreDevice(STORE_ID, StoreDevice.Kind.TV, "카운터 TV", NOW);
        when(pairCodeRepository.findByNonce("nonce-1")).thenReturn(Optional.of(pair));
        when(deviceRepository.findById(100L)).thenReturn(Optional.of(device));

        StoreDto.PairStatusResponse claimed = service.pairStatus("nonce-1", NOW);
        assertThat(claimed.status()).isEqualTo("CLAIMED");
        assertThat(claimed.kind()).isEqualTo("TV");
        // DB엔 해시만
        assertThat(device.getTokenHash()).isEqualTo(StoreDeviceService.hash(claimed.deviceToken()));
        verify(pairCodeRepository).delete(pair);

        // 두 번째 폴링에선 토큰이 다시 안 나온다
        StoreDto.PairStatusResponse again = service.pairStatus("nonce-1", NOW);
        assertThat(again.status()).isEqualTo("EXPIRED");
        assertThat(again.deviceToken()).isNull();
    }

    @Test
    void 입력_전에는_WAITING_10분이_지나면_EXPIRED() {
        StorePairCode pair = pair("111111");
        when(pairCodeRepository.findByNonce("nonce-1")).thenReturn(Optional.of(pair));
        assertThat(service.pairStatus("nonce-1", NOW.plusMinutes(9)).status()).isEqualTo("WAITING");
        assertThat(service.pairStatus("nonce-1", NOW.plusMinutes(10)).status()).isEqualTo("EXPIRED");
    }

    @Test
    void 틀린_숫자나_지난_숫자는_PAIR_CODE_INVALID() {
        when(pairCodeRepository.findLive(anyString(), eq(NOW))).thenReturn(List.of());
        assertThatThrownBy(() -> service.register(STORE_ID, STAFF_ID,
                new StoreDto.RegisterDeviceRequest("000000", "TABLE", "테이블 1"), NOW))
            .extracting("code").isEqualTo(StoreErrorCode.PAIR_CODE_INVALID);
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void 매장당_30대를_넘으면_등록할_수_없다() {
        when(deviceRepository.countByStoreIdAndRevokedAtIsNull(STORE_ID)).thenReturn(30L);
        assertThatThrownBy(() -> service.register(STORE_ID, STAFF_ID,
                new StoreDto.RegisterDeviceRequest("482913", "TV", "TV 31"), NOW))
            .extracting("code").isEqualTo(StoreErrorCode.DEVICE_LIMIT);
    }

    @Test
    void 이_매장_STAFF가_아니면_등록할_수_없다() {
        doThrow(new StoreException(StoreErrorCode.NOT_STAFF_OF_STORE))
            .when(storeAccessService).requireStaffOf(STORE_ID, STAFF_ID);
        assertThatThrownBy(() -> service.register(STORE_ID, STAFF_ID,
                new StoreDto.RegisterDeviceRequest("482913", "TV", "TV"), NOW))
            .extracting("code").isEqualTo(StoreErrorCode.NOT_STAFF_OF_STORE);
    }

    @Test
    void 해제된_기기_토큰은_바로_거절된다() {
        StoreDevice device = new StoreDevice(STORE_ID, StoreDevice.Kind.TABLE, "테이블 1", NOW);
        device.issueToken(StoreDeviceService.hash("tok"));
        when(deviceRepository.findByTokenHash(StoreDeviceService.hash("tok"))).thenReturn(Optional.of(device));
        assertThat(service.authenticate("tok")).isSameAs(device);

        device.revoke(NOW);
        assertThatThrownBy(() -> service.authenticate("tok"))
            .extracting("code").isEqualTo(StoreErrorCode.DEVICE_TOKEN_INVALID);
    }

    @Test
    void 운영을_마친_매장의_기기도_거절된다() {
        StoreDevice device = new StoreDevice(STORE_ID, StoreDevice.Kind.TV, "TV", NOW);
        device.issueToken(StoreDeviceService.hash("tok"));
        when(deviceRepository.findByTokenHash(StoreDeviceService.hash("tok"))).thenReturn(Optional.of(device));
        store.close(NOW);
        assertThatThrownBy(() -> service.authenticate("tok"))
            .extracting("code").isEqualTo(StoreErrorCode.DEVICE_TOKEN_INVALID);
    }

    @Test
    void TV_순서에서_체크인_QR은_뺄_수_없다() {
        assertThatThrownBy(() -> StoreDeviceService.parseOrder(
                new StoreDto.TvSetting(List.of("MVP", "TOP10"), 10, true, List.of())))
            .extracting("code").isEqualTo(StoreErrorCode.INVALID_TV_SETTING);
        assertThatThrownBy(() -> StoreDeviceService.parseOrder(
                new StoreDto.TvSetting(List.of("QR", "QR"), 10, true, List.of())))
            .extracting("code").isEqualTo(StoreErrorCode.INVALID_TV_SETTING);
        assertThat(StoreDeviceService.parseOrder(new StoreDto.TvSetting(List.of("QR", "MVP"), 10, true, List.of())))
            .hasSize(2);
    }
}
