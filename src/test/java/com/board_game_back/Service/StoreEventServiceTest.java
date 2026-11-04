package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreEvent;
import com.board_game_back.Entity.StoreEventUse;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.StoreEventRepository;
import com.board_game_back.Repository.StoreEventUseRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Service.StoreAccessService.ViewerRole;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** 1회용 쿠폰 사용 완료: 매장 멤버만, 기간 안에, 회원당 한 번 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreEventServiceTest {

    private static final Long STORE_ID = 3L;
    private static final Long EVENT_ID = 7L;
    private static final Long MEMBER_ID = 16L;
    // 11/4 14:00 KST
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 5, 0);

    @Mock private StoreRepository storeRepository;
    @Mock private StoreEventRepository eventRepository;
    @Mock private StoreEventUseRepository useRepository;
    @Mock private StoreAccessService storeAccessService;

    private StoreEventService service;
    private StoreEvent event;

    @BeforeEach
    void setUp() {
        service = new StoreEventService(storeRepository, eventRepository, useRepository, storeAccessService);
        Store store = Store.builder().name("야다").address("서울").region("South Korea")
            .openTime(LocalTime.of(10, 0)).closeTime(LocalTime.of(22, 0)).qrSecret("secret").build();
        ReflectionTestUtils.setField(store, "id", STORE_ID);
        event = event(true, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(storeAccessService.viewerRole(STORE_ID, MEMBER_ID)).thenReturn(ViewerRole.MEMBER);
        when(useRepository.findByEventIdAndMemberId(EVENT_ID, MEMBER_ID)).thenReturn(Optional.empty());
    }

    private static StoreEvent event(boolean oneTime, LocalDate from, LocalDate to) {
        StoreEvent e = new StoreEvent(STORE_ID, NOW.minusDays(10));
        e.update("음료 1+1 쿠폰", "아메리카노 1+1", "카운터에 보여 주세요", null, oneTime, from, to);
        ReflectionTestUtils.setField(e, "id", EVENT_ID);
        return e;
    }

    @Test
    void 처음_쓰면_기록하고_쓴_시각을_돌려준다() {
        StoreDto.EventCard card = service.use(STORE_ID, EVENT_ID, MEMBER_ID, NOW);

        verify(useRepository).saveAndFlush(any(StoreEventUse.class));
        assertThat(card.oneTime()).isTrue();
        assertThat(card.usedAt()).isEqualTo(StoreDto.iso(NOW));
    }

    @Test
    void 이미_썼으면_COUPON_ALREADY_USED() {
        when(useRepository.findByEventIdAndMemberId(EVENT_ID, MEMBER_ID))
            .thenReturn(Optional.of(new StoreEventUse(EVENT_ID, MEMBER_ID, NOW.minusDays(1))));

        assertCode(StoreErrorCode.COUPON_ALREADY_USED);
        verify(useRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시에_두_번_눌러_유니크에_걸려도_COUPON_ALREADY_USED() {
        when(useRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_store_event_use"));

        assertCode(StoreErrorCode.COUPON_ALREADY_USED);
    }

    @Test
    void 매장_멤버가_아니면_NOT_MEMBER() {
        when(storeAccessService.viewerRole(STORE_ID, MEMBER_ID)).thenReturn(ViewerRole.STAFF);

        assertCode(StoreErrorCode.NOT_MEMBER);
    }

    @Test
    void 기간_밖이면_EVENT_NOT_ACTIVE() {
        event.update("음료 1+1 쿠폰", null, null, null, true, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 30));

        assertCode(StoreErrorCode.EVENT_NOT_ACTIVE);
    }

    @Test
    void 쿠폰이_아닌_이벤트는_EVENT_NOT_COUPON() {
        event.update("평일 1+1", null, null, null, false, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertCode(StoreErrorCode.EVENT_NOT_COUPON);
    }

    private void assertCode(StoreErrorCode code) {
        assertThatThrownBy(() -> service.use(STORE_ID, EVENT_ID, MEMBER_ID, NOW))
            .isInstanceOf(StoreException.class)
            .satisfies(e -> assertThat(((StoreException) e).code()).isEqualTo(code));
    }
}
