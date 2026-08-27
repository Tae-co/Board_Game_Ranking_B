package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.MatchParticipant;
import com.board_game_back.Entity.MatchRecord;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.RoomRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SeasonServiceTest {

    private static final String PERIOD = "2026-08";

    @Mock private CommunityRepository communityRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private MatchRecordRepository matchRecordRepository;

    @InjectMocks private SeasonService seasonService;

    private Member 태윤;   // 최다승
    private Member 지민;   // 최대 상승폭
    private Member 현우;   // 이번 달 데뷔 = 다크호스
    private BoardGame 카탄;
    private BoardGame 아줄;
    private Room room;

    @BeforeEach
    void setUp() {
        태윤 = member(1L, "태윤");
        지민 = member(2L, "지민");
        현우 = member(3L, "현우");
        카탄 = boardGame(10L, "카탄");
        아줄 = boardGame(11L, "아줄");
        room = new Room("금요모임", "ABC123", 10L);
        ReflectionTestUtils.setField(room, "id", 100L);
    }

    @Test
    void getPeriods_경기가_있는_달만_최신순으로_반환한다() {
        when(roomRepository.findByCommunityId(1L)).thenReturn(List.of(room));
        when(matchRecordRepository.findPlayedAtByRoomIds(anyList())).thenReturn(List.of(
            LocalDateTime.of(2026, 8, 3, 20, 0),
            LocalDateTime.of(2026, 8, 17, 20, 0),
            LocalDateTime.of(2026, 6, 1, 20, 0)
        ));

        List<SeasonDto.PeriodResponse> periods = seasonService.getPeriods(1L);

        assertThat(periods).extracting(SeasonDto.PeriodResponse::period)
            .containsExactly("2026-08", "2026-06");
        assertThat(periods.get(0).matchCount()).isEqualTo(2);
    }

    @Test
    void getPeriods_방이_없으면_빈_목록이다() {
        when(roomRepository.findByCommunityId(1L)).thenReturn(List.of());

        assertThat(seasonService.getPeriods(1L)).isEmpty();
    }

    @Test
    void getSummary_최다승과_최대상승폭과_다크호스를_각각_다른_사람에게_준다() {
        givenCommunityWithRoom();
        // 카탄 2판: 태윤이 모두 1등
        // 아줄 1판: 지민 1등
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of(
            match(카탄, LocalDateTime.of(2026, 8, 3, 20, 0),
                placing(태윤, 1, 10), placing(지민, 2, -5), placing(현우, 3, -5)),
            match(카탄, LocalDateTime.of(2026, 8, 10, 20, 0),
                placing(태윤, 1, 8), placing(지민, 2, -4), placing(현우, 3, -4)),
            match(아줄, LocalDateTime.of(2026, 8, 17, 20, 0),
                placing(지민, 1, 60), placing(현우, 2, 20), placing(태윤, 3, -30))
        ));
        // 현우만 이번 달에 커뮤니티 데뷔
        when(matchRecordRepository.findFirstPlayedAtByMember(anyList())).thenReturn(List.of(
            new Object[]{1L, LocalDateTime.of(2026, 5, 1, 20, 0)},
            new Object[]{2L, LocalDateTime.of(2026, 5, 1, 20, 0)},
            new Object[]{3L, LocalDateTime.of(2026, 8, 3, 20, 0)}
        ));

        SeasonDto.SummaryResponse summary = seasonService.getSummary(1L, PERIOD);

        assertThat(summary.totalMatches()).isEqualTo(3);
        assertThat(summary.totalPlayers()).isEqualTo(3);

        Map<String, SeasonDto.Award> awards = summary.awards().stream()
            .collect(Collectors.toMap(SeasonDto.Award::type, Function.identity()));
        assertThat(awards.get("MOST_WINS").nickname()).isEqualTo("태윤");
        assertThat(awards.get("MOST_WINS").value()).isEqualTo(2.0);      // 2승
        assertThat(awards.get("BIGGEST_CLIMB").nickname()).isEqualTo("지민");
        assertThat(awards.get("BIGGEST_CLIMB").value()).isEqualTo(51.0); // -5 -4 +60
        assertThat(awards.get("DARK_HORSE").nickname()).isEqualTo("현우");
        assertThat(awards.get("DARK_HORSE").value()).isEqualTo(11.0);    // -5 -4 +20
    }

    @Test
    void getSummary_한_사람이_두_상을_동시에_받지_않는다() {
        givenCommunityWithRoom();
        // 태윤이 최다승이자 최대 상승폭 — 상승폭은 그 다음 사람에게 간다
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of(
            match(카탄, LocalDateTime.of(2026, 8, 3, 20, 0),
                placing(태윤, 1, 100), placing(지민, 2, 20))
        ));
        when(matchRecordRepository.findFirstPlayedAtByMember(anyList())).thenReturn(List.of());

        SeasonDto.SummaryResponse summary = seasonService.getSummary(1L, PERIOD);

        assertThat(summary.awards()).extracting(SeasonDto.Award::nickname)
            .containsExactly("태윤", "지민");
        assertThat(summary.awards()).extracting(SeasonDto.Award::type)
            .containsExactly("MOST_WINS", "BIGGEST_CLIMB");
    }

    @Test
    void getSummary_게임별_1위는_그_게임의_승수로_뽑는다() {
        givenCommunityWithRoom();
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of(
            match(카탄, LocalDateTime.of(2026, 8, 3, 20, 0), placing(태윤, 1, 10), placing(지민, 2, -5)),
            match(카탄, LocalDateTime.of(2026, 8, 4, 20, 0), placing(태윤, 1, 8), placing(지민, 2, -4)),
            match(아줄, LocalDateTime.of(2026, 8, 5, 20, 0), placing(지민, 1, 30), placing(태윤, 2, -10))
        ));
        when(matchRecordRepository.findFirstPlayedAtByMember(anyList())).thenReturn(List.of());

        SeasonDto.SummaryResponse summary = seasonService.getSummary(1L, PERIOD);

        assertThat(summary.gameTops()).extracting(
                SeasonDto.GameTop::boardGameName, SeasonDto.GameTop::nickname, SeasonDto.GameTop::wins)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("카탄", "태윤", 2),
                org.assertj.core.groups.Tuple.tuple("아줄", "지민", 1));
    }

    @Test
    void getSummary_경기가_없는_달은_비어있는_결산을_돌려준다() {
        givenCommunityWithRoom();
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of());

        SeasonDto.SummaryResponse summary = seasonService.getSummary(1L, PERIOD);

        assertThat(summary.totalMatches()).isZero();
        assertThat(summary.totalPlayers()).isZero();
        assertThat(summary.awards()).isEmpty();
        assertThat(summary.gameTops()).isEmpty();
        assertThat(summary.inviteCode()).isEqualTo("XYZ789");
    }

    @Test
    void getSummary_시즌_형식이_잘못되면_거부한다() {
        assertThatThrownBy(() -> seasonService.getSummary(1L, "2026년8월"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── fixtures ──

    private void givenCommunityWithRoom() {
        Community community = new Community("금요보드", "서울", null, 1L);
        ReflectionTestUtils.setField(community, "id", 1L);
        community.assignInviteCode("XYZ789");
        when(communityRepository.findById(1L)).thenReturn(Optional.of(community));
        when(roomRepository.findByCommunityId(1L)).thenReturn(List.of(room));
    }

    private Member member(Long id, String nickname) {
        Member member = Member.builder().nickname(nickname).build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private BoardGame boardGame(Long id, String name) {
        BoardGame game = BoardGame.builder().name(name).build();
        ReflectionTestUtils.setField(game, "id", id);
        return game;
    }

    private record Placing(Member member, int placement, double ratingChange) {}

    private Placing placing(Member member, int placement, double ratingChange) {
        return new Placing(member, placement, ratingChange);
    }

    private MatchRecord match(BoardGame game, LocalDateTime playedAt, Placing... placings) {
        MatchRecord record = MatchRecord.builder().boardGame(game).room(room).build();
        ReflectionTestUtils.setField(record, "playedAt", playedAt);
        for (Placing p : placings) {
            // MatchParticipant 생성자가 record.participants에 스스로 등록한다
            MatchParticipant.builder()
                .matchRecord(record).member(p.member()).placement(p.placement()).build()
                .updateRatingChange(p.ratingChange());
        }
        return record;
    }
}
