package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreMatch;
import com.board_game_back.Entity.StoreMatchParticipant;
import com.board_game_back.Entity.StoreRating;
import com.board_game_back.Entity.StoreRatingReset;
import com.board_game_back.Entity.StoreSeason;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.StoreMatchParticipantRepository;
import com.board_game_back.Repository.StoreMatchRepository;
import com.board_game_back.Repository.StoreRatingRepository;
import com.board_game_back.Repository.StoreRatingResetRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Repository.StoreRoomRepository;
import com.board_game_back.Repository.StoreSeasonRepository;
import com.board_game_back.Repository.StoreSeasonSnapshotRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

/** 매장 시즌: 주기·기본 이름·이번 시즌 다시 계산(초기화 반영) (plan-store §2.2 시즌, §6.5) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreSeasonServiceTest {

    private static final Long STORE_ID = 3L;
    private static final Long GAME = 1L;
    private static final LocalDate START = LocalDate.of(2026, 11, 1);

    @Mock private StoreRepository storeRepository;
    @Mock private StoreSeasonRepository seasonRepository;
    @Mock private StoreSeasonSnapshotRepository snapshotRepository;
    @Mock private StoreRatingRepository ratingRepository;
    @Mock private StoreRatingResetRepository resetRepository;
    @Mock private StoreMatchRepository matchRepository;
    @Mock private StoreMatchParticipantRepository participantRepository;
    @Mock private StoreRoomRepository roomRepository;
    @Mock private BoardGameRepository boardGameRepository;
    @Mock private StoreRankingService rankingService;

    private StoreSeasonService service;
    private Store store;

    @BeforeEach
    void setUp() {
        service = new StoreSeasonService(storeRepository, seasonRepository, snapshotRepository, ratingRepository,
            resetRepository, matchRepository, participantRepository, roomRepository, boardGameRepository,
            rankingService, new StoreEloCalculator());
        store = Store.builder().name("야다").address("서울").region("South Korea")
            .openTime(LocalTime.of(10, 0)).closeTime(LocalTime.of(22, 0)).qrSecret("s").build();
        ReflectionTestUtils.setField(store, "id", STORE_ID);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
    }

    @Test
    void 매월은_한_달_분기는_석_달() {
        assertThat(StoreSeasonService.cycleEnd("MONTHLY", START)).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(StoreSeasonService.cycleEnd("QUARTERLY", LocalDate.of(2026, 12, 1))).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(StoreSeasonService.defaultName("MONTHLY", START)).isEqualTo("11월 시즌");
        assertThat(StoreSeasonService.defaultName("QUARTERLY", LocalDate.of(2027, 1, 1))).isEqualTo("2027 1분기 시즌");
    }

    private StoreMatch match(long id, LocalDateTime endedAt) {
        StoreMatch m = org.mockito.Mockito.mock(StoreMatch.class);
        when(m.getId()).thenReturn(id);
        when(m.getBoardGameId()).thenReturn(GAME);
        when(m.getEndedAt()).thenReturn(endedAt);
        return m;
    }

    private static StoreMatchParticipant participant(long matchId, long memberId, int placement) {
        StoreMatchParticipant p = org.mockito.Mockito.mock(StoreMatchParticipant.class);
        when(p.getStoreMatchId()).thenReturn(matchId);
        when(p.getMemberId()).thenReturn(memberId);
        when(p.getPlacement()).thenReturn(placement);
        return p;
    }

    @Test
    @SuppressWarnings("unchecked")
    void 다시_계산하면_초기화_뒤_경기는_500_위에_쌓인다() {
        StoreSeason season = new StoreSeason(STORE_ID, "11월 시즌", START, LocalDate.of(2026, 11, 30), LocalDateTime.now());
        LocalDateTime t1 = LocalDateTime.of(2026, 11, 2, 5, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 11, 3, 5, 0);
        List<StoreMatch> matches = List.of(match(10, t1), match(11, t2));
        when(matchRepository.findByStoreIdAndStatusAndBusinessDateBetweenOrderByEndedAtAsc(eq(STORE_ID), eq(StoreMatch.Status.RECORDED), eq(START), any()))
            .thenReturn(matches);
        List<StoreMatchParticipant> participants = List.of(
            participant(10, 1, 1), participant(10, 2, 2),
            participant(11, 1, 1), participant(11, 2, 2));
        when(participantRepository.findByStoreMatchIdIn(any())).thenReturn(participants);
        // 1번 경기 뒤, 2번 경기 전에 회원 1 초기화
        StoreRatingReset reset = new StoreRatingReset(STORE_ID, GAME, 1L, 10L, 650, "b", LocalDateTime.of(2026, 11, 2, 12, 0));
        when(resetRepository.findByStoreIdAndUndoneAtIsNullOrderByResetAtAsc(STORE_ID)).thenReturn(List.of(reset));

        service.replay(store, season);

        ArgumentCaptor<Collection<StoreRating>> saved = ArgumentCaptor.forClass(Collection.class);
        verify(ratingRepository).deleteByStoreId(STORE_ID);
        verify(ratingRepository).saveAll(saved.capture());
        StoreRating one = new ArrayList<>(saved.getValue()).stream().filter(r -> r.getMemberId() == 1L).findFirst().orElseThrow();
        StoreRating two = new ArrayList<>(saved.getValue()).stream().filter(r -> r.getMemberId() == 2L).findFirst().orElseThrow();
        assertThat(one.getGames()).isEqualTo(2);
        StoreEloCalculator elo = new StoreEloCalculator();
        double[] first = elo.calculate(List.of(new StoreEloCalculator.Player(500, 0, 1), new StoreEloCalculator.Player(500, 0, 2)));
        double[] second = elo.calculate(List.of(new StoreEloCalculator.Player(500, 1, 1), new StoreEloCalculator.Player(first[1], 1, 2)));
        assertThat(one.getRating()).isEqualTo(second[0]);
        assertThat(two.getRating()).isEqualTo(second[1]);
    }

    @Test
    void 처음_조회하면_이번_달_시즌이_생긴다() {
        when(seasonRepository.findByStoreIdOrderByStartDateAsc(anyLong())).thenReturn(List.of());
        when(seasonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        StoreSeason s = service.ensureCurrent(STORE_ID, LocalDateTime.of(2026, 11, 15, 5, 0));
        assertThat(s.getStartDate()).isEqualTo(START);
        assertThat(s.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(s.getName()).isEqualTo("11월 시즌");
    }
}
