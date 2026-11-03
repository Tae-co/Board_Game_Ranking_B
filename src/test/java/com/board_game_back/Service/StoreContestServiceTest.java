package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreContest;
import com.board_game_back.Entity.StoreContestEntry;
import com.board_game_back.Entity.StoreMatch;
import com.board_game_back.Entity.StoreMatchParticipant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 하루 대회 순위: 그 판 인원 기준 순위 점수 합 → 게임 점수 합 → 공동 순위, 나간 사람은 순위에서 빠짐 (plan-store §1 대회 점수) */
class StoreContestServiceTest {

    private final StoreContest contest = contest(3);
    private final List<StoreMatch> matches = new ArrayList<>();
    private final Map<Long, List<StoreMatchParticipant>> byMatch = new HashMap<>();
    private final Map<Long, String> names = Map.of(1L, "가", 2L, "나", 3L, "다", 4L, "라");

    @Test
    void 순위_점수는_그_판_인원_기준() {
        assertThat(StoreContestService.points(4, 1)).isEqualTo(4);
        assertThat(StoreContestService.points(4, 4)).isEqualTo(1);
        assertThat(StoreContestService.points(3, 1)).isEqualTo(3);
        assertThat(StoreContestService.points(3, 3)).isEqualTo(1);
    }

    @Test
    void 순위_점수가_같으면_게임_점수_합으로() {
        round(1, new long[] {1, 2, 3, 4}, new double[] {10, 8, 6, 5});
        round(2, new long[] {2, 1, 4, 3}, new double[] {10, 7, 6, 4});

        List<StoreDto.ContestStanding> s = standings(entries(1, 2, 3, 4));

        // 가 4+3=7 (17점), 나 3+4=7 (18점) → 나가 1위
        assertThat(s).extracting(StoreDto.ContestStanding::nickname).containsExactly("나", "가", "라", "다");
        assertThat(s).extracting(StoreDto.ContestStanding::rank).containsExactly(1, 2, 3, 4);
        assertThat(s.get(0).points()).containsExactly(3, 4, null);
        assertThat(s.get(0).total()).isEqualTo(7);
    }

    @Test
    void 순위_점수와_게임_점수가_모두_같으면_공동_순위() {
        round(1, new long[] {1, 2, 3}, new double[] {5, 5, 5});
        round(2, new long[] {2, 1, 3}, new double[] {5, 5, 5});

        List<StoreDto.ContestStanding> s = standings(entries(1, 2, 3));

        assertThat(s).extracting(StoreDto.ContestStanding::rank).containsExactly(1, 1, 3);
    }

    @Test
    void 반영_전_판은_점수에_안_들어간다() {
        round(1, new long[] {1, 2, 3}, new double[] {9, 8, 7});
        StoreMatch open = round(2, new long[] {3, 2, 1}, new double[] {9, 8, 7});
        when(open.getStatus()).thenReturn(StoreMatch.Status.AWAITING_STAFF);

        List<StoreDto.ContestStanding> s = standings(entries(1, 2, 3));

        assertThat(s.get(0).nickname()).isEqualTo("가");
        assertThat(s.get(0).points()).containsExactly(3, null, null);
    }

    @Test
    void 대회_중_나간_사람은_점수는_남고_순위에서_빠진다() {
        round(1, new long[] {1, 2, 3, 4}, new double[] {10, 8, 6, 5});
        List<StoreContestEntry> entries = entries(1, 2, 3, 4);
        entries.get(0).leave(LocalDateTime.of(2026, 11, 28, 6, 0));

        List<StoreDto.ContestStanding> s = standings(entries);

        assertThat(s).extracting(StoreDto.ContestStanding::nickname).containsExactly("나", "다", "라", "가");
        assertThat(s.get(3).left()).isTrue();
        assertThat(s.get(3).rank()).isNull();
        assertThat(s.get(3).total()).isEqualTo(4);
        assertThat(s.get(0).rank()).isEqualTo(1);
    }

    @Test
    void 하루_대회는_영업일이_지나면_취소_대상() {
        Store store = Store.builder().name("야다").address("서울").region("South Korea")
            .openTime(LocalTime.of(14, 0)).closeTime(LocalTime.of(2, 0)).qrSecret("s").build();
        StoreContest c = contest(3);
        c.update(1L, 1L, "카탄 대회", LocalDateTime.of(2026, 11, 28, 4, 0), LocalDate.of(2026, 11, 28), 4, 3, null);

        // 11/29 01:00 KST(= 11/28 16:00 UTC)는 아직 11/28 영업일
        assertThat(StoreContestService.isDayOver(store, c, LocalDateTime.of(2026, 11, 28, 16, 0))).isFalse();
        assertThat(StoreContestService.isToday(store, c, LocalDateTime.of(2026, 11, 28, 16, 0))).isTrue();
        // 11/29 14:00 KST(= 11/29 05:00 UTC)부터 다음 영업일
        assertThat(StoreContestService.isDayOver(store, c, LocalDateTime.of(2026, 11, 29, 5, 0))).isTrue();
    }

    // ── 도우미 ──

    private List<StoreDto.ContestStanding> standings(List<StoreContestEntry> entries) {
        return StoreContestService.standings(contest, entries, matches, byMatch, names, 1L);
    }

    private StoreMatch round(int round, long[] memberByPlacement, double[] scores) {
        long matchId = 100 + round;
        StoreMatch m = mock(StoreMatch.class);
        when(m.getId()).thenReturn(matchId);
        when(m.getContestRound()).thenReturn(round);
        when(m.getStatus()).thenReturn(StoreMatch.Status.RECORDED);
        List<StoreMatchParticipant> ps = new ArrayList<>();
        for (int i = 0; i < memberByPlacement.length; i++) {
            StoreMatchParticipant p = new StoreMatchParticipant(matchId, memberByPlacement[i]);
            p.setResult(i + 1, scores[i], null);
            ps.add(p);
        }
        matches.add(m);
        byMatch.put(matchId, ps);
        return m;
    }

    private static List<StoreContestEntry> entries(long... memberIds) {
        return new ArrayList<>(Arrays.stream(memberIds).mapToObj(id -> {
            StoreContestEntry e = new StoreContestEntry(9L, id, LocalDateTime.of(2026, 11, 20, 0, 0));
            e.play(null);
            return e;
        }).toList());
    }

    private static StoreContest contest(int rounds) {
        StoreContest c = new StoreContest(3L, LocalDateTime.of(2026, 11, 1, 0, 0));
        c.update(1L, 1L, "카탄 대회", LocalDateTime.of(2026, 11, 28, 4, 0), LocalDate.of(2026, 11, 28), 4, rounds, null);
        return c;
    }
}
