package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.StoreMatchParticipantRepository;
import com.board_game_back.Repository.StoreMatchRepository;
import com.board_game_back.Repository.StoreRatingRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Repository.StoreRoomRepository;
import com.board_game_back.Repository.StoreSeasonRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 매장 MVP 최다승·최대 연승·최고 승률(3판 이상) (plan-store §1 MVP) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreRankingServiceMvpTest {

    private static final Long STORE_ID = 3L;
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    @Mock private StoreRepository storeRepository;
    @Mock private StoreRoomRepository storeRoomRepository;
    @Mock private StoreRatingRepository storeRatingRepository;
    @Mock private StoreMatchRepository storeMatchRepository;
    @Mock private StoreMatchParticipantRepository participantRepository;
    @Mock private BoardGameRepository boardGameRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private StorePartyService partyService;
    @Mock private StoreMatchService matchService;
    @Mock private StoreSeasonRepository seasonRepository;

    private StoreRankingService service;

    @BeforeEach
    void setUp() {
        service = new StoreRankingService(storeRepository, storeRoomRepository, storeRatingRepository, storeMatchRepository,
            participantRepository, boardGameRepository, memberRepository, partyService, matchService, seasonRepository);
        List<Member> members = List.of(member(1L, "가"), member(2L, "나"), member(3L, "다"));
        when(memberRepository.findAllById(any())).thenReturn(members);
    }

    private static Member member(Long id, String nickname) {
        Member m = mock(Member.class);
        when(m.getId()).thenReturn(id);
        when(m.getNickname()).thenReturn(nickname);
        return m;
    }

    /** [memberId, 참여 판 수, 방문 일수, 마지막 기록] */
    private static Object[] activity(long id, long games, long days, int lastDay) {
        return new Object[] { id, games, days, LocalDateTime.of(2026, 11, lastDay, 20, 0) };
    }

    private void given(List<Object[]> activity, List<Object[]> placements) {
        when(participantRepository.activityBetween(anyLong(), any(), any())).thenReturn(new ArrayList<>(activity));
        when(participantRepository.placementsBetween(anyLong(), any(), any())).thenReturn(new ArrayList<>(placements));
    }

    private static Object[] p(long id, int placement) {
        return new Object[] { id, placement };
    }

    @Test
    void 최다승_최대연승_최고승률() {
        given(List.of(activity(1, 6, 3, 10), activity(2, 4, 2, 9), activity(3, 2, 1, 8)),
            List.of(
                p(1, 1), p(2, 2),
                p(1, 1), p(2, 2),
                p(1, 2), p(2, 1),   // 1번 연승 2에서 끊김
                p(1, 1), p(2, 1),   // 공동 1등도 승
                p(1, 1), p(3, 1),
                p(1, 2), p(3, 1)));
        StoreDto.Mvp mvp = service.mvpBetween(STORE_ID, FROM, TO);

        assertThat(mvp.mostWins().memberId()).isEqualTo(1L);
        assertThat(mvp.mostWins().count()).isEqualTo(4);
        assertThat(mvp.bestStreak().memberId()).isEqualTo(1L);   // 1번 2연승, 3번 2연승 → 동점이면 최근(1번)
        assertThat(mvp.bestStreak().count()).isEqualTo(2);
        // 3번은 2판 100%지만 3판 미만이라 제외. 1번 4/6 = 67%, 2번 2/4 = 50%
        assertThat(mvp.bestWinRate().memberId()).isEqualTo(1L);
        assertThat(mvp.bestWinRate().percent()).isEqualTo(67);
        assertThat(mvp.bestWinRate().games()).isEqualTo(6);
    }

    @Test
    void 아무도_못_이긴_칸은_비움() {
        given(List.<Object[]>of(activity(1, 3, 1, 10)), List.of(p(1, 2), p(1, 2), p(1, 3)));
        StoreDto.Mvp mvp = service.mvpBetween(STORE_ID, FROM, TO);

        assertThat(mvp.mostVisits().memberId()).isEqualTo(1L);
        assertThat(mvp.mostWins()).isNull();
        assertThat(mvp.bestStreak()).isNull();
        assertThat(mvp.bestWinRate()).isNull();
    }
}
