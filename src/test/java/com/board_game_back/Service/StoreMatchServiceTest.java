package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreMatch;
import com.board_game_back.Entity.StoreMatchParticipant;
import com.board_game_back.Entity.StoreMember;
import com.board_game_back.Entity.StoreParty;
import com.board_game_back.Entity.StorePartyMember;
import com.board_game_back.Entity.StoreRating;
import com.board_game_back.Entity.StoreRoom;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.StoreMatchParticipantRepository;
import com.board_game_back.Repository.StoreMatchRepository;
import com.board_game_back.Repository.StoreMemberRepository;
import com.board_game_back.Repository.StoreRatingRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Repository.StoreRoomRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 매장 판 시간 규칙 (plan-store §2.1 6번, §6.5) — 저장 마감·최소 시간·다시 저장·자동 종료·기록 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreMatchServiceTest {

    private static final Long STORE_ID = 3L;
    private static final Long ROOM_ID = 5L;
    private static final Long PARTY_ID = 9L;
    private static final Long MATCH_ID = 100L;
    private static final Long RECORDER = 1L;

    @Mock private StoreRepository storeRepository;
    @Mock private StoreRoomRepository storeRoomRepository;
    @Mock private StoreMemberRepository storeMemberRepository;
    @Mock private StoreMatchRepository storeMatchRepository;
    @Mock private StoreMatchParticipantRepository participantRepository;
    @Mock private StoreRatingRepository storeRatingRepository;
    @Mock private BoardGameRepository boardGameRepository;
    @Mock private StoreAccessService storeAccessService;
    @Mock private StorePartyService partyService;
    @Mock private StorePartyNotifier notifier;

    private StoreMatchService service;
    private Store store;
    private StoreParty party;
    private StoreMatch saved;
    private final List<StoreMatchParticipant> participants = new ArrayList<>();
    private final Map<Long, StoreRating> ratings = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        service = new StoreMatchService(storeRepository, storeRoomRepository, storeMemberRepository, storeMatchRepository,
            participantRepository, storeRatingRepository, boardGameRepository, storeAccessService, partyService,
            new StoreEloCalculator(), notifier, null);
        useStore(LocalTime.of(12, 0), LocalTime.of(22, 0));

        BoardGame catan = BoardGame.builder().name("카탄").minPlayers(3).maxPlayers(4).build();
        catan.updateRecordMinutes(20, 60);
        ReflectionTestUtils.setField(catan, "id", 1L);
        when(boardGameRepository.findById(1L)).thenReturn(Optional.of(catan));
        StoreRoom room = new StoreRoom(STORE_ID, 1L);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        when(storeRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

        party = new StoreParty(STORE_ID, "1234", RECORDER, LocalDate.of(2026, 11, 2), LocalDateTime.of(2026, 11, 2, 3, 0));
        ReflectionTestUtils.setField(party, "id", PARTY_ID);
        List<StorePartyMember> members = List.of(pm(1L), pm(2L), pm(3L));
        when(partyService.membership(anyLong())).thenAnswer(inv -> members.stream()
            .filter(m -> m.getMemberId().equals(inv.getArgument(0))).findFirst());
        when(partyService.findParty(PARTY_ID)).thenReturn(Optional.of(party));
        when(partyService.lock(PARTY_ID)).thenReturn(party);
        when(partyService.openMatch(PARTY_ID)).thenReturn(Optional.empty());
        when(partyService.activeMembers(PARTY_ID)).thenReturn(members);
        when(partyService.nicknames(any())).thenReturn(Map.of());

        StoreMember checkedIn = new StoreMember(STORE_ID, 1L, 1, LocalDateTime.of(2026, 11, 2, 0, 0));
        checkedIn.checkIn(LocalDateTime.of(2030, 1, 1, 0, 0));
        when(storeMemberRepository.findByStoreIdAndMemberId(any(), any())).thenReturn(Optional.of(checkedIn));

        when(storeMatchRepository.save(any())).thenAnswer(inv -> {
            saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", MATCH_ID);
            return saved;
        });
        when(storeMatchRepository.findById(MATCH_ID)).thenAnswer(inv -> Optional.ofNullable(saved));
        when(storeMatchRepository.findPartyIdById(MATCH_ID)).thenReturn(Optional.of(PARTY_ID));
        when(participantRepository.save(any())).thenAnswer(inv -> {
            participants.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(participantRepository.findByStoreMatchIdOrderByIdAsc(MATCH_ID)).thenReturn(participants);
        when(storeRatingRepository.findByStoreIdAndBoardGameIdAndMemberId(any(), any(), any()))
            .thenAnswer(inv -> Optional.ofNullable(ratings.get((Long) inv.getArgument(2))));
        when(storeRatingRepository.save(any())).thenAnswer(inv -> {
            StoreRating r = inv.getArgument(0);
            ratings.put(r.getMemberId(), r);
            return r;
        });
    }

    // 2026-11-02 KST = UTC+9
    private static LocalDateTime kst(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute).minusHours(9);
    }

    @Test
    void 저장_마감은_시작_더하기_최대_시간() {
        service.start(STORE_ID, ROOM_ID, RECORDER, kst(2, 14, 0));
        assertThat(saved.getSaveDeadline()).isEqualTo(kst(2, 15, 0));
        assertThat(saved.getClosesAt()).isEqualTo(kst(2, 22, 0));
        assertThat(participants).extracting(StoreMatchParticipant::getMemberId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void 마감이_가까우면_저장_마감이_영업_마감으로_당겨진다() {
        service.start(STORE_ID, ROOM_ID, RECORDER, kst(2, 21, 30));
        assertThat(saved.getSaveDeadline()).isEqualTo(kst(2, 22, 0));
    }

    @Test
    void 자정을_넘기는_매장은_다음날_마감() {
        useStore(LocalTime.of(14, 0), LocalTime.of(2, 0));
        service.start(STORE_ID, ROOM_ID, RECORDER, kst(3, 1, 30));
        assertThat(saved.getSaveDeadline()).isEqualTo(kst(3, 2, 0));
        assertThat(saved.getBusinessDate()).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void 마감까지_최소_시간보다_적게_남으면_시작_못_함() {
        assertThatThrownBy(() -> service.start(STORE_ID, ROOM_ID, RECORDER, kst(2, 21, 50)))
            .isInstanceOf(StoreException.class)
            .extracting(e -> ((StoreException) e).code()).isEqualTo(StoreErrorCode.TOO_CLOSE_TO_CLOSING);
    }

    @Test
    void 영업시간_밖이면_시작_못_함() {
        assertThatThrownBy(() -> service.start(STORE_ID, ROOM_ID, RECORDER, kst(2, 23, 0)))
            .extracting(e -> ((StoreException) e).code()).isEqualTo(StoreErrorCode.STORE_NOT_OPEN);
    }

    @Test
    void 기록자가_아니면_시작_못_함() {
        assertThatThrownBy(() -> service.start(STORE_ID, ROOM_ID, 2L, kst(2, 14, 0)))
            .extracting(e -> ((StoreException) e).code()).isEqualTo(StoreErrorCode.NOT_RECORDER);
    }

    @Test
    void 최소_시간_전엔_저장_못_함() {
        service.start(STORE_ID, ROOM_ID, RECORDER, kst(2, 14, 0));
        assertThatThrownBy(() -> service.submit(MATCH_ID, RECORDER, result(), kst(2, 14, 19)))
            .extracting(e -> ((StoreException) e).code()).isEqualTo(StoreErrorCode.SAVE_TOO_EARLY);
    }

    @Test
    void 저장_마감_뒤_처음_저장은_막힘() {
        service.start(STORE_ID, ROOM_ID, RECORDER, kst(2, 14, 0));
        assertThatThrownBy(() -> service.submit(MATCH_ID, RECORDER, result(), kst(2, 15, 1)))
            .extracting(e -> ((StoreException) e).code()).isEqualTo(StoreErrorCode.SAVE_DEADLINE_PASSED);
    }

    @Test
    void 점수가_달라요_뒤엔_최대_시간이_지나도_영업_마감_전이면_다시_저장() {
        service.start(STORE_ID, ROOM_ID, RECORDER, kst(2, 14, 0));
        service.submit(MATCH_ID, RECORDER, result(), kst(2, 14, 50));
        service.dispute(MATCH_ID, 2L);
        assertThat(saved.getStatus()).isEqualTo(StoreMatch.Status.DISPUTED);

        service.submit(MATCH_ID, RECORDER, result(), kst(2, 16, 0));
        assertThat(saved.getStatus()).isEqualTo(StoreMatch.Status.AWAITING_CONFIRM);
        assertThat(saved.getFirstSavedAt()).isEqualTo(kst(2, 14, 50));
    }

    @Test
    void 전원_확인하면_기록되고_매장_점수가_바뀐다() {
        service.start(STORE_ID, ROOM_ID, RECORDER, kst(2, 14, 0));
        service.submit(MATCH_ID, RECORDER, result(), kst(2, 14, 30));
        service.confirm(MATCH_ID, 2L);
        assertThat(saved.getStatus()).isEqualTo(StoreMatch.Status.AWAITING_CONFIRM);
        service.confirm(MATCH_ID, 3L);

        assertThat(saved.getStatus()).isEqualTo(StoreMatch.Status.RECORDED);
        assertThat(saved.getEndReason()).isEqualTo(StoreMatch.EndReason.CONFIRMED);
        assertThat(ratings.get(1L).getRating()).isGreaterThan(500);
        assertThat(ratings.get(1L).getWins()).isEqualTo(1);
        assertThat(participants.get(0).getRatingBefore()).isEqualTo(500);
    }

    @Test
    void 저장_없이_저장_마감이_지나면_EXPIRED() {
        service.start(STORE_ID, ROOM_ID, RECORDER, kst(2, 14, 0));
        assertThat(service.closeIfDue(MATCH_ID, kst(2, 14, 59))).isFalse();
        assertThat(service.closeIfDue(MATCH_ID, kst(2, 15, 1))).isTrue();
        assertThat(saved.getStatus()).isEqualTo(StoreMatch.Status.EXPIRED);
        assertThat(saved.getEndReason()).isEqualTo(StoreMatch.EndReason.EXPIRED_MAX);
    }

    @Test
    void 영업_마감으로_당겨진_판은_EXPIRED_CLOSE() {
        service.start(STORE_ID, ROOM_ID, RECORDER, kst(2, 21, 30));
        service.closeIfDue(MATCH_ID, kst(2, 22, 1));
        assertThat(saved.getEndReason()).isEqualTo(StoreMatch.EndReason.EXPIRED_CLOSE);
    }

    @Test
    void 확인_5분이_지나면_이대로_기록() {
        service.start(STORE_ID, ROOM_ID, RECORDER, kst(2, 14, 0));
        service.submit(MATCH_ID, RECORDER, result(), kst(2, 14, 30));
        assertThat(service.closeIfDue(MATCH_ID, kst(2, 14, 34))).isFalse();
        assertThat(service.closeIfDue(MATCH_ID, kst(2, 14, 36))).isTrue();
        assertThat(saved.getStatus()).isEqualTo(StoreMatch.Status.RECORDED);
        assertThat(saved.getEndReason()).isEqualTo(StoreMatch.EndReason.TIMEOUT);
    }

    @Test
    void 참가자와_맞지_않는_결과는_거부() {
        service.start(STORE_ID, ROOM_ID, RECORDER, kst(2, 14, 0));
        StoreDto.SubmitRequest missing = new StoreDto.SubmitRequest(List.of(
            new StoreDto.SubmitParticipant(1L, 1, 10.0, null),
            new StoreDto.SubmitParticipant(2L, 2, 8.0, null)));
        assertThatThrownBy(() -> service.submit(MATCH_ID, RECORDER, missing, kst(2, 14, 30)))
            .extracting(e -> ((StoreException) e).code()).isEqualTo(StoreErrorCode.INVALID_RESULT);
    }

    private static StoreDto.SubmitRequest result() {
        return new StoreDto.SubmitRequest(List.of(
            new StoreDto.SubmitParticipant(1L, 1, 10.0, null),
            new StoreDto.SubmitParticipant(2L, 2, 8.0, null),
            new StoreDto.SubmitParticipant(3L, 3, 7.0, null)));
    }

    private StorePartyMember pm(Long memberId) {
        return new StorePartyMember(PARTY_ID, memberId, LocalDateTime.of(2026, 11, 2, 3, (int) (long) memberId));
    }

    private void useStore(LocalTime open, LocalTime close) {
        store = Store.builder().name("야다").address("서울").region("South Korea")
            .openTime(open).closeTime(close).qrSecret("secret").build();
        ReflectionTestUtils.setField(store, "id", STORE_ID);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
    }
}
