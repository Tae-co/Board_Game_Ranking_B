package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.MatchParticipant;
import com.board_game_back.Entity.MatchRecord;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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


    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private MatchRecordRepository matchRecordRepository;
    @Mock private PlayerGameRatingRepository playerGameRatingRepository;
    @Mock private MemberRepository memberRepository;

    @InjectMocks private SeasonService seasonService;

    private Member 태윤;
    private Member 지민;
    private Member 현우;
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
    void getStatus_기간_없이_전체_경기를_보고_from은_첫_경기_날짜다() {
        givenCommunityWithRoom();
        when(matchRecordRepository.findByRoomIds(anyList())).thenReturn(List.of(
            // 2026-05-01 20:00 UTC = 05-02 05:00 KST
            match(카탄, LocalDateTime.of(2026, 5, 1, 20, 0), placing(태윤, 1, 10), placing(지민, 2, -10)),
            match(카탄, LocalDateTime.of(2026, 8, 1, 20, 0), placing(지민, 1, 10), placing(태윤, 2, -10))
        ));

        SeasonDto.StatusResponse status = seasonService.getStatus(1L);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        assertThat(status.matchCount()).isEqualTo(2);
        assertThat(status.from()).isEqualTo(LocalDate.of(2026, 5, 2));
        assertThat(status.to()).isEqualTo(today);
        assertThat(status.windowDays()).isEqualTo((int) ChronoUnit.DAYS.between(LocalDate.of(2026, 5, 2), today) + 1);
    }

    @Test
    void getStatus_게임별_최고점수는_게임이_겹치지_않게_3칸을_채운다() {
        givenCommunityWithRoom();
        when(matchRecordRepository.findByRoomIds(anyList())).thenReturn(List.of());
        Room 일요모임 = new Room("일요모임", "DEF456", 11L);
        ReflectionTestUtils.setField(일요모임, "id", 101L);
        BoardGame 스플렌더 = boardGame(12L, "스플렌더");
        Member 민서 = member(4L, "민서");
        when(playerGameRatingRepository.findByRoomIdsWithMinPlays(anyList(), eq(3))).thenReturn(List.of(
            rating(태윤, 카탄, room, 1300),
            rating(지민, 카탄, 일요모임, 1200),   // 카탄은 이미 나왔다 — 방이 달라도 건너뛴다
            rating(태윤, 아줄, room, 1100),       // 같은 사람이 다른 게임으로 또 오를 수 있다
            rating(현우, 아줄, 일요모임, 1000),
            rating(민서, 스플렌더, room, 900),
            rating(지민, boardGame(13L, "테라포밍"), room, 800)
        ));

        SeasonDto.StatusResponse status = seasonService.getStatus(1L);

        assertThat(status.leaders()).extracting(SeasonDto.Leader::boardGameName).containsExactly("카탄", "아줄", "스플렌더");
        assertThat(status.leaders()).extracting(SeasonDto.Leader::nickname).containsExactly("태윤", "태윤", "민서");
        assertThat(status.leaders()).extracting(SeasonDto.Leader::displayScore).containsExactly(1300.0, 1100.0, 900.0);
        assertThat(status.leaders()).extracting(SeasonDto.Leader::rank).containsExactly(1, 2, 3);
    }

    @Test
    void getStatus_항목별_기록을_독립적으로_준다() {
        givenCommunityWithRoom();
        when(communityMemberRepository.countByCommunityId(1L)).thenReturn(7L);
        // 태윤 4승(연승 2), 지민 3연승, 현우는 7연패
        when(matchRecordRepository.findByRoomIds(anyList())).thenReturn(List.of(
            match(카탄, at(3), placing(태윤, 1, 10), placing(지민, 2, -5), placing(현우, 3, -5)),
            match(카탄, at(4), placing(태윤, 1, 8), placing(지민, 2, -4), placing(현우, 3, -4)),
            match(아줄, at(10), placing(지민, 1, 20), placing(태윤, 2, -6), placing(현우, 3, -6)),
            match(아줄, at(11), placing(지민, 1, 18), placing(태윤, 2, -5), placing(현우, 3, -5)),
            match(카탄, at(12), placing(지민, 1, 16), placing(현우, 2, 40), placing(태윤, 3, -8)),
            match(카탄, at(17), placing(태윤, 1, 12), placing(지민, 2, -7), placing(현우, 3, -7)),
            match(아줄, at(18), placing(태윤, 1, 11), placing(지민, 2, -6), placing(현우, 3, -6))
        ));

        Map<String, SeasonDto.Award> awards = awardsByType(seasonService.getStatus(1L));

        assertThat(awards.get("LAST_WINNER").nickname()).isEqualTo("태윤");
        assertThat(awards.get("LAST_WINNER").boardGameName()).isEqualTo("아줄");
        assertThat(awards.get("MOST_WINS").nickname()).isEqualTo("태윤");
        assertThat(awards.get("MOST_WINS").value()).isEqualTo(4.0);
        assertThat(awards.get("LONGEST_STREAK").nickname()).isEqualTo("지민");
        assertThat(awards.get("LONGEST_STREAK").value()).isEqualTo(3.0);
        assertThat(awards.get("LONGEST_LOSING_STREAK").nickname()).isEqualTo("현우");
        assertThat(awards.get("LONGEST_LOSING_STREAK").value()).isEqualTo(7.0);
        // 7판은 승률 최소 판수(10)에 못 미친다
        assertThat(awards).doesNotContainKey("BEST_WIN_RATE");
        // 셋 다 7판 — 동점은 승수로 가른다
        assertThat(awards.get("MOST_PLAYED").nickname()).isEqualTo("태윤");
        assertThat(awards.get("MOST_PLAYED").value()).isEqualTo(7.0);
    }

    @Test
    void getStatus_한_사람이_여러_상을_받을_수_있다() {
        givenCommunityWithRoom();
        // 태윤이 3연승 후 1패 — 최다승·최대 연승·최다 경기 모두 태윤
        when(matchRecordRepository.findByRoomIds(anyList())).thenReturn(List.of(
            match(카탄, at(3), placing(태윤, 1, 30), placing(지민, 2, -10)),
            match(카탄, at(4), placing(태윤, 1, 28), placing(지민, 2, -9)),
            match(카탄, at(5), placing(태윤, 1, 26), placing(지민, 2, -8)),
            match(카탄, at(6), placing(지민, 1, 20), placing(태윤, 2, -7), placing(현우, 3, -1))
        ));

        Map<String, SeasonDto.Award> awards = awardsByType(seasonService.getStatus(1L));

        assertThat(awards.get("MOST_WINS").nickname()).isEqualTo("태윤");
        assertThat(awards.get("LONGEST_STREAK").nickname()).isEqualTo("태윤");
        assertThat(awards.get("MOST_PLAYED").nickname()).isEqualTo("태윤");
        assertThat(awards.get("LONGEST_LOSING_STREAK").nickname()).isEqualTo("지민");
    }

    @Test
    void getStatus_승률은_10판_이상만_보고_퍼센트로_준다() {
        givenCommunityWithRoom();
        List<MatchRecord> matches = new ArrayList<>();
        // 태윤 10판 6승, 지민 10판 4승, 현우는 2판 2승(100%)이지만 판수 미달
        for (int i = 0; i < 10; i++) {
            matches.add(i < 6
                ? match(카탄, at(i + 1), placing(태윤, 1, 5), placing(지민, 2, -5))
                : match(카탄, at(i + 1), placing(지민, 1, 5), placing(태윤, 2, -5)));
        }
        matches.add(match(카탄, at(20), placing(현우, 1, 5), placing(지민, 2, -5)));
        matches.add(match(카탄, at(21), placing(현우, 1, 5), placing(태윤, 2, -5)));
        when(matchRecordRepository.findByRoomIds(anyList())).thenReturn(matches);

        Map<String, SeasonDto.Award> awards = awardsByType(seasonService.getStatus(1L));

        assertThat(awards.get("BEST_WIN_RATE").nickname()).isEqualTo("태윤");
        assertThat(awards.get("BEST_WIN_RATE").value()).isEqualTo(6.0 / 11 * 100);
    }

    @Test
    void getStatus_1연승_1연패뿐이면_연승_연패는_주지_않는다() {
        givenCommunityWithRoom();
        when(matchRecordRepository.findByRoomIds(anyList())).thenReturn(List.of(
            match(카탄, at(3), placing(태윤, 1, 10), placing(지민, 2, -10)),
            match(카탄, at(4), placing(지민, 1, 12), placing(태윤, 2, -12))
        ));

        SeasonDto.StatusResponse summary = seasonService.getStatus(1L);

        assertThat(summary.awards()).extracting(SeasonDto.Award::type)
            .containsExactly("LAST_WINNER", "MOST_WINS", "MOST_PLAYED");
    }

    @Test
    void getStatus_불참한_경기는_연승을_끊지_않는다() {
        givenCommunityWithRoom();
        // 태윤은 1·3번째 판에서 이겼고 2번째 판에는 없다 → 2연승이다
        when(matchRecordRepository.findByRoomIds(anyList())).thenReturn(List.of(
            match(카탄, at(3), placing(태윤, 1, 10), placing(지민, 2, -10)),
            match(카탄, at(4), placing(지민, 1, 12), placing(현우, 2, -12)),
            match(카탄, at(5), placing(태윤, 1, 9), placing(현우, 2, -9))
        ));

        Map<String, SeasonDto.Award> awards = awardsByType(seasonService.getStatus(1L));

        assertThat(awards.get("LONGEST_STREAK").nickname()).isEqualTo("태윤");
        assertThat(awards.get("LONGEST_STREAK").value()).isEqualTo(2.0);
    }

    @Test
    void getStatus_마지막_경기가_공동_1등이면_모두_준다() {
        givenCommunityWithRoom();
        when(matchRecordRepository.findByRoomIds(anyList())).thenReturn(List.of(
            match(카탄, at(3), placing(현우, 1, 10), placing(지민, 2, -10)),
            match(아줄, at(4), placing(태윤, 1, 5), placing(지민, 1, 5), placing(현우, 3, -10))
        ));

        SeasonDto.StatusResponse summary = seasonService.getStatus(1L);

        assertThat(summary.awards()).filteredOn(a -> a.type().equals("LAST_WINNER"))
            .extracting(SeasonDto.Award::nickname).containsExactlyInAnyOrder("태윤", "지민");
    }

    @Test
    void getStatus_점수판을_가장_많이_저장한_사람을_마지막에_준다() {
        givenCommunityWithRoom();
        // 지민이 2판, 태윤이 1판 기록. 기록자가 없는 옛 경기는 세지 않는다
        when(matchRecordRepository.findByRoomIds(anyList())).thenReturn(List.of(
            match(카탄, at(1), placing(태윤, 1, 10), placing(지민, 2, -10)),
            match(카탄, at(2), 1L, placing(태윤, 1, 10), placing(지민, 2, -10)),
            match(카탄, at(3), 2L, placing(태윤, 1, 10), placing(지민, 2, -10)),
            match(카탄, at(4), 2L, placing(지민, 1, 10), placing(태윤, 2, -10))
        ));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(지민));

        SeasonDto.StatusResponse summary = seasonService.getStatus(1L);

        SeasonDto.Award last = summary.awards().get(summary.awards().size() - 1);
        assertThat(last.type()).isEqualTo("MOST_RECORDED");
        assertThat(last.nickname()).isEqualTo("지민");
        assertThat(last.value()).isEqualTo(2.0);
    }

    @Test
    void getStatus_경기가_없으면_비어있는_현황을_돌려준다() {
        givenCommunityWithRoom();
        when(communityMemberRepository.countByCommunityId(1L)).thenReturn(4L);
        when(matchRecordRepository.findByRoomIds(anyList())).thenReturn(List.of());

        SeasonDto.StatusResponse summary = seasonService.getStatus(1L);

        // 경기가 없어도 방·인원은 커뮤니티의 현재 값이라 0이 아니다
        assertThat(summary.totalRooms()).isEqualTo(1);
        assertThat(summary.totalMembers()).isEqualTo(4L);
        assertThat(summary.awards()).isEmpty();
        assertThat(summary.leaders()).isEmpty();
        assertThat(summary.windowDays()).isEqualTo(1);
    }

    private Map<String, SeasonDto.Award> awardsByType(SeasonDto.StatusResponse status) {
        return status.awards().stream()
            .collect(Collectors.toMap(SeasonDto.Award::type, Function.identity()));
    }

    // ── fixtures ──

    private void givenCommunityWithRoom() {
        Community community = new Community("금요보드", "서울", null, 1L);
        ReflectionTestUtils.setField(community, "id", 1L);
        community.assignInviteCode("XYZ789");
        when(communityRepository.findById(1L)).thenReturn(Optional.of(community));
        when(roomRepository.findByCommunityId(1L)).thenReturn(List.of(room));
    }

    /** 경기 시각 — 쿼리가 시간순으로 주므로 순서만 의미가 있다. */
    private LocalDateTime at(int day) {
        return LocalDateTime.of(2026, 9, day, 20, 0);
    }

    private Member member(Long id, String nickname) {
        Member member = Member.builder().nickname(nickname).build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private PlayerGameRating rating(Member member, BoardGame game, Room room, double score) {
        PlayerGameRating rating = new PlayerGameRating(member, game, room);
        ReflectionTestUtils.setField(rating.getGameStats(), "rating", score);
        return rating;
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
        return match(game, playedAt, null, placings);
    }

    private MatchRecord match(BoardGame game, LocalDateTime playedAt, Long recordedBy, Placing... placings) {
        MatchRecord record = MatchRecord.builder().boardGame(game).room(room).recordedByMemberId(recordedBy).build();
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
