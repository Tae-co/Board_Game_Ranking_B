package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    @InjectMocks private SeasonService seasonService;

    private Member 태윤;   // 최다승
    private Member 지민;   // 최대 연승
    private Member 현우;   // 기간 안 데뷔 = 다크호스
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
    void getStatus_조회_범위는_커뮤니티_타임존의_최근_30일이다() {
        givenCommunityWithRoom();
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of());

        SeasonDto.StatusResponse status = seasonService.getStatus(1L);

        // 오늘 포함 30일: (오늘-29) 00:00 KST ~ 내일 00:00 KST, UTC로 옮겨서 자른다
        ZoneId 서울 = ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(서울);
        verify(matchRecordRepository).findByRoomIdsAndPlayedAtRange(
            anyList(),
            eq(LocalDateTime.ofInstant(today.minusDays(29).atStartOfDay(서울).toInstant(), ZoneOffset.UTC)),
            eq(LocalDateTime.ofInstant(today.plusDays(1).atStartOfDay(서울).toInstant(), ZoneOffset.UTC)));
        assertThat(status.from()).isEqualTo(today.minusDays(29));
        assertThat(status.to()).isEqualTo(today);
        assertThat(status.windowDays()).isEqualTo(30);
    }

    @Test
    void getStatus_시상대는_사람마다_최고_점수인_방으로_줄_세운다() {
        givenCommunityWithRoom();
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of());
        Room 일요모임 = new Room("일요모임", "DEF456", 11L);
        ReflectionTestUtils.setField(일요모임, "id", 101L);
        Member 민서 = member(4L, "민서");
        when(playerGameRatingRepository.findByRoomIdsWithMinPlays(anyList(), eq(3))).thenReturn(List.of(
            rating(태윤, room, 900), rating(태윤, 일요모임, 1300),  // 태윤은 일요모임 점수로 오른다
            rating(지민, room, 1100),
            rating(현우, 일요모임, 700),
            rating(민서, room, 600)
        ));

        SeasonDto.StatusResponse status = seasonService.getStatus(1L);

        assertThat(status.leaders()).extracting(SeasonDto.Leader::nickname).containsExactly("태윤", "지민", "현우");
        assertThat(status.leaders()).extracting(SeasonDto.Leader::roomName).containsExactly("일요모임", "금요모임", "일요모임");
        assertThat(status.leaders()).extracting(SeasonDto.Leader::displayScore).containsExactly(1300.0, 1100.0, 700.0);
        assertThat(status.leaders()).extracting(SeasonDto.Leader::rank).containsExactly(1, 2, 3);
    }

    @Test
    void getStatus_최다승과_최대연승과_다크호스를_각각_다른_사람에게_준다() {
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
        // 현우만 기간 안에 커뮤니티 데뷔
        when(matchRecordRepository.findFirstPlayedAtByMember(anyList())).thenReturn(List.of(
            new Object[]{1L, LocalDateTime.of(2026, 5, 1, 20, 0)},
            new Object[]{2L, LocalDateTime.of(2026, 5, 1, 20, 0)},
            new Object[]{3L, LocalDateTime.now(ZoneOffset.UTC).minusDays(5)} // 30일 안
        ));

        SeasonDto.StatusResponse summary = seasonService.getStatus(1L);

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
    void getStatus_1연승뿐이면_최대연승은_주지_않는다() {
        givenCommunityWithRoom();
        when(communityMemberRepository.countByCommunityId(1L)).thenReturn(3L);
        // 태윤·지민이 번갈아 이겨서 아무도 2연승이 없다
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of(
            match(카탄, LocalDateTime.of(2026, 8, 3, 20, 0), placing(태윤, 1, 10), placing(지민, 2, -10)),
            match(카탄, LocalDateTime.of(2026, 8, 4, 20, 0), placing(지민, 1, 12), placing(태윤, 2, -12))
        ));
        when(matchRecordRepository.findFirstPlayedAtByMember(anyList())).thenReturn(List.of());

        SeasonDto.StatusResponse summary = seasonService.getStatus(1L);

        assertThat(summary.awards()).extracting(SeasonDto.Award::type).containsExactly("MOST_WINS");
    }

    @Test
    void getStatus_불참한_경기는_연승을_끊지_않는다() {
        givenCommunityWithRoom();
        when(communityMemberRepository.countByCommunityId(1L)).thenReturn(3L);
        // 태윤은 1·3번째 판에서 이겼고 2번째 판에는 없다 → 2연승이다
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of(
            match(카탄, LocalDateTime.of(2026, 8, 3, 20, 0), placing(태윤, 1, 10), placing(지민, 2, -10)),
            match(카탄, LocalDateTime.of(2026, 8, 4, 20, 0), placing(지민, 1, 12), placing(현우, 2, -12)),
            match(카탄, LocalDateTime.of(2026, 8, 5, 20, 0), placing(태윤, 1, 9), placing(현우, 2, -9))
        ));
        when(matchRecordRepository.findFirstPlayedAtByMember(anyList())).thenReturn(List.of());

        SeasonDto.StatusResponse summary = seasonService.getStatus(1L);

        Map<String, SeasonDto.Award> awards = summary.awards().stream()
            .collect(Collectors.toMap(SeasonDto.Award::type, Function.identity()));
        assertThat(awards.get("MOST_WINS").nickname()).isEqualTo("태윤");
        assertThat(awards.get("MOST_WINS").value()).isEqualTo(2.0);
        // 태윤이 이미 최다승을 받았으므로 연승 상은 2연승이 없는 지민에게 가지 않는다
        assertThat(awards).doesNotContainKey("LONGEST_STREAK");
    }

    @Test
    void getStatus_한_사람이_두_상을_동시에_받지_않는다() {
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

        SeasonDto.StatusResponse summary = seasonService.getStatus(1L);

        assertThat(summary.awards()).extracting(SeasonDto.Award::nickname)
            .containsExactly("태윤", "지민");
        assertThat(summary.awards()).extracting(SeasonDto.Award::type)
            .containsExactly("MOST_WINS", "LONGEST_STREAK");
        assertThat(summary.awards().get(1).value()).isEqualTo(2.0); // 지민의 최대 연승
    }

    @Test
    void getStatus_경기가_없으면_비어있는_현황을_돌려준다() {
        givenCommunityWithRoom();
        when(communityMemberRepository.countByCommunityId(1L)).thenReturn(4L);
        when(matchRecordRepository.findByRoomIdsAndPlayedAtRange(anyList(), any(), any())).thenReturn(List.of());

        SeasonDto.StatusResponse summary = seasonService.getStatus(1L);

        // 경기가 없어도 방·인원은 커뮤니티의 현재 값이라 0이 아니다
        assertThat(summary.totalRooms()).isEqualTo(1);
        assertThat(summary.totalMembers()).isEqualTo(4L);
        assertThat(summary.awards()).isEmpty();
        assertThat(summary.leaders()).isEmpty();
        assertThat(summary.inviteCode()).isEqualTo("XYZ789");
    }

    // ── fixtures ──

    private void givenCommunityWithRoom() {
        Community community = new Community("금요보드", "서울", null, 1L);
        ReflectionTestUtils.setField(community, "id", 1L);
        community.assignInviteCode("XYZ789");
        when(communityRepository.findById(1L)).thenReturn(Optional.of(community));
        when(roomRepository.findByCommunityId(1L)).thenReturn(List.of(room));
    }

    /** 기간 안의 날짜 — 경기 시각 자체는 쿼리 목이 거르므로 순서만 의미가 있다. */
    private LocalDateTime at(int day) {
        return LocalDateTime.of(2026, 9, day, 20, 0);
    }

    private Member member(Long id, String nickname) {
        Member member = Member.builder().nickname(nickname).build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private PlayerGameRating rating(Member member, Room room, double score) {
        PlayerGameRating rating = new PlayerGameRating(member, 카탄, room);
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
