package com.board_game_back.Service;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreEvent;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.StoreEventRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Utils.StoreClock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 매장 이벤트 (음료 1+1 등, 6j·6k). 게임방·랭킹 없이 매장 홈 카드와 TV 4d에만 뜬다. 기간은 매장 영업일. */
@Service
@RequiredArgsConstructor
public class StoreEventService {

    private final StoreRepository storeRepository;
    private final StoreEventRepository eventRepository;

    /** 매장 홈 카드: 진행 중인 것만 */
    public List<StoreDto.EventCard> activeCards(Store store) {
        LocalDate today = today(store);
        return eventRepository.findByStoreIdAndEndsOnGreaterThanEqualOrderByStartsOnAsc(store.getId(), today).stream()
            .filter(e -> e.isActiveOn(today))
            .map(e -> card(e, today))
            .toList();
    }

    /** 어드민 6 이벤트 목록·6k, 손님 2 이벤트 더보기: 진행 중 + 예정 */
    public List<StoreDto.EventCard> adminList(Long storeId) {
        Store store = getStore(storeId);
        LocalDate today = today(store);
        return eventRepository.findByStoreIdAndEndsOnGreaterThanEqualOrderByStartsOnAsc(storeId, today).stream()
            .map(e -> card(e, today))
            .toList();
    }

    /** 월간 리포트: 그 달과 겹친 이벤트 */
    public List<StoreDto.ReportEvent> reportEvents(Long storeId, LocalDate from, LocalDate to) {
        return eventRepository.findByStoreIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqualOrderByStartsOnAsc(storeId, to, from)
            .stream()
            .map(e -> new StoreDto.ReportEvent("EVENT", e.getTitle(), e.getStartsOn() + "~" + e.getEndsOn(), 0, List.of(), null))
            .toList();
    }

    @Transactional
    public List<StoreDto.EventCard> create(Long storeId, StoreDto.EventForm form) {
        Store store = getStore(storeId);
        if (store.isClosed()) throw new StoreException(StoreErrorCode.STORE_CLOSED);
        StoreEvent event = new StoreEvent(storeId, StorePartyService.now());
        apply(event, form);
        eventRepository.save(event);
        return adminList(storeId);
    }

    @Transactional
    public List<StoreDto.EventCard> update(Long storeId, Long eventId, StoreDto.EventForm form) {
        apply(getEvent(storeId, eventId), form);
        return adminList(storeId);
    }

    @Transactional
    public List<StoreDto.EventCard> delete(Long storeId, Long eventId) {
        eventRepository.delete(getEvent(storeId, eventId));
        return adminList(storeId);
    }

    /** 운영 끝내기(6f): 진행 중·예정 이벤트 취소 */
    @Transactional
    public void onStoreClosed(Store store) {
        eventRepository.deleteByStoreIdAndEndsOnGreaterThanEqual(store.getId(), today(store));
    }

    private static void apply(StoreEvent event, StoreDto.EventForm form) {
        if (form == null) throw new StoreException(StoreErrorCode.INVALID_EVENT);
        String title = form.title() == null ? "" : form.title().trim();
        String benefit = form.benefitText() == null || form.benefitText().isBlank() ? null : form.benefitText().trim();
        String image = form.imageUrl() == null || form.imageUrl().isBlank() ? null : form.imageUrl().trim();
        if (title.isEmpty() || title.length() > 40 || (benefit != null && benefit.length() > 300)
            || (image != null && image.length() > 500)) {
            throw new StoreException(StoreErrorCode.INVALID_EVENT);
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(form.startsOn());
            end = LocalDate.parse(form.endsOn());
        } catch (RuntimeException e) {
            throw new StoreException(StoreErrorCode.INVALID_EVENT);
        }
        if (end.isBefore(start)) throw new StoreException(StoreErrorCode.INVALID_EVENT);
        event.update(title, benefit, image, start, end);
    }

    private static StoreDto.EventCard card(StoreEvent e, LocalDate today) {
        return new StoreDto.EventCard(e.getId(), e.getTitle(), e.getBenefitText(), e.getImageUrl(),
            e.getStartsOn().toString(), e.getEndsOn().toString(), e.isActiveOn(today));
    }

    private static LocalDate today(Store store) {
        return StoreClock.businessDate(store, StorePartyService.now());
    }

    private StoreEvent getEvent(Long storeId, Long eventId) {
        return eventRepository.findById(eventId)
            .filter(e -> e.getStoreId().equals(storeId))
            .orElseThrow(() -> new StoreException(StoreErrorCode.EVENT_NOT_FOUND));
    }

    private Store getStore(Long storeId) {
        return storeRepository.findById(storeId).orElseThrow(() -> new StoreException(StoreErrorCode.STORE_NOT_FOUND));
    }
}
