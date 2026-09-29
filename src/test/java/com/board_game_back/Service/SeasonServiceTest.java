package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.MatchParticipant;
import com.board_game_back.Entity.MatchRecord;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Utils.RegionTimeZones;
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
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private MatchRecordRepository matchRecordRepository;
    // 결산에 시상대를 얹으면서 생긴 의존. 이 테스트가 보는 건 수상 3종이고, 목은 빈 목록을 준다.
    @Mock private SeasonArchiveService archiveService;
    @Mock private SeasonBoundaryService boundaryService;

    @InjectMocks private SeasonService seasonService;

    private Member 태윤;   // 최다승
    private Member 지민;   // 최대 연승
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
        when(boundaryService.zoneOfCommunity(1L)).thenReturn(RegionTimeZones.DEFAULT);
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
    void getPeriods_달은_커뮤니티_타임존으로_가른다() {
        when(roomRepository.findByCommunityId(1L)).thenReturn(List.of(room));
        when(boundaryService.zoneOfCommunity(1L)).thenReturn(RegionTimeZones.DEFAULT);
        // playedAt은 UTC 벽시계. UTC 8/31 15:30 = KST 9/1 00:30 → 9월, UTC 8/31 14:59 = KST 8/31 23:59 → 8월
        when(matchRecordRepository.findPlayedAtByRoomIds(anyList())).thenReturn(List.of(
            LocalDateTime.of(2026, 8, 31, 15, 30),
            LocalDateTime.of(2026, 8, 31, 14, 59)
        ));

        List<SeasonDto.PeriodResponse> periods = seasonService.getPeriods(1L);

        assertThat(periods).extracting(SeasonDto.PeriodResponse::period)
            .containsExactly("2026-09", "2026-08");
    }

    @Test
    void getSummary_조회_범위는_커뮤니티_타임존의_1일_00시를_UTC로_옮긴_값이다() {
        givenCommunityWithRoom();
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of());

        seasonService.getSummary(1L, PERIOD);

        // KST 8/1 00:00 = UTC 7/31 15:00, KST 9/1 00:00 = UTC 8/31 15:00
        verify(matchRecordRepository).findByRoomIdsAndPlayedAtRange(
            anyList(), eq(LocalDateTime.of(2026, 7, 31, 15, 0)), eq(LocalDateTime.of(2026, 8, 31, 15, 0)));
    }

    @Test
    void getPeriods_방이_없으면_빈_목록이다() {
        when(roomRepository.findByCommunityId(1L)).thenReturn(List.of());

        assertThat(seasonService.getPeriods(1L)).isEmpty();
    }

    @Test
    void getSummary_최다승과_최대연승과_다크호스를_각각_다른_사람에게_준다() {
        givenCommunityWithRoom();
        when(communityMemberRepository.countByCommunityId(1L)).thenReturn(7L);
        // 태윤 4승이지만 연승은 2까지, 지민 3승인데 내리 3연승 — 승수와 연승이 갈리는 배치다
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of(
            match(카탄, LocalDateTime.of(2026, 8, 3, 20, 0),
                placing(태윤, 1, 10), placing(지민, 2, -5), placing(현우, 3, -5)),
            match(카탄, LocalDateTime.of(2026, 8, 4, 20, 0),
                placing(태윤, 1, 8), placing(지민, 2, -4), placing(현우, 3, -4)),
            match(아줄, LocalDateTime.of(2026, 8, 10, 20, 0),
                placing(지민, 1, 20), placing(태윤, 2, -6), placing(현우, 3, -6)),
            match(아줄, LocalDateTime.of(2026, 8, 11, 20, 0),
                placing(지민, 1, 18), placing(태윤, 2, -5), placing(현우, 3, -5)),
            match(카탄, LocalDateTime.of(2026, 8, 12, 20, 0),
                placing(지민, 1, 16), placing(현우, 2, 40), placing(태윤, 3, -8)),
            match(카탄, LocalDateTime.of(2026, 8, 17, 20, 0),
                placing(태윤, 1, 12), placing(지민, 2, -7), placing(현우, 3, -7)),
            match(카탄, LocalDateTime.of(2026, 8, 18, 20, 0),
                placing(태윤, 1, 11), placing(지민, 2, -6), placing(현우, 3, -6))
        ));
        // 현우만 이번 달에 커뮤니티 데뷔
        when(matchRecordRepository.findFirstPlayedAtByMember(anyList())).thenReturn(List.of(
            new Object[]{1L, LocalDateTime.of(2026, 5, 1, 20, 0)},
            new Object[]{2L, LocalDateTime.of(2026, 5, 1, 20, 0)},
            new Object[]{3L, LocalDateTime.of(2026, 8, 3, 20, 0)}
        ));

        SeasonDto.SummaryResponse summary = seasonService.getSummary(1L, PERIOD);

        assertThat(summary.totalRooms()).isEqualTo(1);
        assertThat(summary.totalMembers()).isEqualTo(7L);

        Map<String, SeasonDto.Award> awards = summary.awards().stream()
            .collect(Collectors.toMap(SeasonDto.Award::type, Function.identity()));
        assertThat(awards.get("MOST_WINS").nickname()).isEqualTo("태윤");
        assertThat(awards.get("MOST_WINS").value()).isEqualTo(4.0);
        // 태윤의 최대 연승은 2(1·2판, 6·7판), 지민은 3(3·4·5판) — 승수 1위와 연승 1위가 다르다
        assertThat(awards.get("LONGEST_STREAK").nickname()).isEqualTo("지민");
        assertThat(awards.get("LONGEST_STREAK").value()).isEqualTo(3.0);
        assertThat(awards.get("DARK_HORSE").nickname()).isEqualTo("현우");
        assertThat(awards.get("DARK_HORSE").value()).isEqualTo(7.0);     // -5 -4 -6 -5 +40 -7 -6
    }

    @Test
    void getSummary_1연승뿐이면_최대연승은_주지_않는다() {
        givenCommunityWithRoom();
        when(communityMemberRepository.countByCommunityId(1L)).thenReturn(3L);
        // 태윤·지민이 번갈아 이겨서 아무도 2연승이 없다
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of(
            match(카탄, LocalDateTime.of(2026, 8, 3, 20, 0), placing(태윤, 1, 10), placing(지민, 2, -10)),
            match(카탄, LocalDateTime.of(2026, 8, 4, 20, 0), placing(지민, 1, 12), placing(태윤, 2, -12))
        ));
        when(matchRecordRepository.findFirstPlayedAtByMember(anyList())).thenReturn(List.of());

        SeasonDto.SummaryResponse summary = seasonService.getSummary(1L, PERIOD);

        assertThat(summary.awards()).extracting(SeasonDto.Award::type).containsExactly("MOST_WINS");
    }

    @Test
    void getSummary_불참한_경기는_연승을_끊지_않는다() {
        givenCommunityWithRoom();
        when(communityMemberRepository.countByCommunityId(1L)).thenReturn(3L);
        // 태윤은 1·3번째 판에서 이겼고 2번째 판에는 없다 → 2연승이다
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of(
            match(카탄, LocalDateTime.of(2026, 8, 3, 20, 0), placing(태윤, 1, 10), placing(지민, 2, -10)),
            match(카탄, LocalDateTime.of(2026, 8, 4, 20, 0), placing(지민, 1, 12), placing(현우, 2, -12)),
            match(카탄, LocalDateTime.of(2026, 8, 5, 20, 0), placing(태윤, 1, 9), placing(현우, 2, -9))
        ));
        when(matchRecordRepository.findFirstPlayedAtByMember(anyList())).thenReturn(List.of());

        SeasonDto.SummaryResponse summary = seasonService.getSummary(1L, PERIOD);

        Map<String, SeasonDto.Award> awards = summary.awards().stream()
            .collect(Collectors.toMap(SeasonDto.Award::type, Function.identity()));
        assertThat(awards.get("MOST_WINS").nickname()).isEqualTo("태윤");
        assertThat(awards.get("MOST_WINS").value()).isEqualTo(2.0);
        // 태윤이 이미 최다승을 받았으므로 연승 상은 2연승이 없는 지민에게 가지 않는다
        assertThat(awards).doesNotContainKey("LONGEST_STREAK");
    }

    @Test
    void getSummary_한_사람이_두_상을_동시에_받지_않는다() {
        givenCommunityWithRoom();
        when(communityMemberRepository.countByCommunityId(1L)).thenReturn(3L);
        // 태윤이 최다승이자 최대 연승(3연승) — 연승 상은 2연승인 지민에게 간다
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of(
            match(카탄, LocalDateTime.of(2026, 8, 3, 20, 0), placing(태윤, 1, 30), placing(지민, 2, -10)),
            match(카탄, LocalDateTime.of(2026, 8, 4, 20, 0), placing(태윤, 1, 28), placing(지민, 2, -9)),
            match(카탄, LocalDateTime.of(2026, 8, 5, 20, 0), placing(태윤, 1, 26), placing(지민, 2, -8)),
            match(카탄, LocalDateTime.of(2026, 8, 6, 20, 0), placing(지민, 1, 20), placing(태윤, 2, -7)),
            match(카탄, LocalDateTime.of(2026, 8, 7, 20, 0), placing(지민, 1, 18), placing(태윤, 2, -6))
        ));
        when(matchRecordRepository.findFirstPlayedAtByMember(anyList())).thenReturn(List.of());

        SeasonDto.SummaryResponse summary = seasonService.getSummary(1L, PERIOD);

        assertThat(summary.awards()).extracting(SeasonDto.Award::nickname)
            .containsExactly("태윤", "지민");
        assertThat(summary.awards()).extracting(SeasonDto.Award::type)
            .containsExactly("MOST_WINS", "LONGEST_STREAK");
        assertThat(summary.awards().get(1).value()).isEqualTo(2.0); // 지민의 최대 연승
    }

    @Test
    void getSummary_경기가_없는_달은_비어있는_결산을_돌려준다() {
        givenCommunityWithRoom();
        when(communityMemberRepository.countByCommunityId(1L)).thenReturn(4L);
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of());

        SeasonDto.SummaryResponse summary = seasonService.getSummary(1L, PERIOD);

        // 경기가 없어도 방·인원은 커뮤니티의 현재 값이라 0이 아니다
        assertThat(summary.totalRooms()).isEqualTo(1);
        assertThat(summary.totalMembers()).isEqualTo(4L);
        assertThat(summary.awards()).isEmpty();
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
