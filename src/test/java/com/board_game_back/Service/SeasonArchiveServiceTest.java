package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.RankingDto;
import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.SeasonRankSnapshot;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Repository.SeasonRankSnapshotRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SeasonArchiveServiceTest {

    private static final Long ROOM_ID = 100L;
    private static final Long GAME_ID = 10L;
    private static final String SEASON = "2026-08";

    @Mock private SeasonRankSnapshotRepository snapshotRepository;
    @Mock private MatchRecordRepository matchRecordRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private BoardGameRepository boardGameRepository;
    @Mock private SeasonBoundaryService boundaryService;

    @InjectMocks private SeasonArchiveService archiveService;

    private Room room;
    private BoardGame 카탄;

    @BeforeEach
    void setUp() {
        room = new Room("금요모임", "ABC123", GAME_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        room.assignCommunity(1L);

        카탄 = boardGame(GAME_ID, "카탄");
    }

    // ── ③ 지난 시즌 순위표 ──

    @Test
    void 스냅샷은_현재_랭킹과_같은_필드로_나온다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 12, 8);
        givenSnapshots(snapshot(1, 태윤));
        givenMembersExist(태윤);

        List<RankingDto.GameRankingResponse> ranking =
            archiveService.getSeasonRanking(ROOM_ID, GAME_ID, SEASON);

        assertThat(ranking).hasSize(1);
        RankingDto.GameRankingResponse row = ranking.get(0);
        assertThat(row.rank()).isEqualTo(1);
        assertThat(row.memberId()).isEqualTo(1L);
        assertThat(row.nickname()).isEqualTo("태윤");
        assertThat(row.rating()).isCloseTo(1250.0, within(0.001)); // 표시 점수다
        assertThat(row.playCount()).isEqualTo(12);
        assertThat(row.winCount()).isEqualTo(8);
        assertThat(row.loseCount()).isEqualTo(4);
    }

    @Test
    void 시즌_형식이_아니면_조회하지_않는다() {
        assertThat(archiveService.getSeasonRanking(ROOM_ID, GAME_ID, "2026-8월")).isEmpty();
        verify(snapshotRepository, never())
            .findByRoomIdAndBoardGameIdAndSeasonKeyOrderByRankAsc(anyLong(), anyLong(), anyString());
    }

    @Test
    void 탈퇴한_멤버의_스냅샷_행은_버린다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 3, 2);
        PlayerGameRating 유령 = rating(99L, "탈퇴자", 950.0, 3, 1);
        givenSnapshots(snapshot(1, 태윤), snapshot(2, 유령));
        givenMembersExist(태윤); // 유령은 member 테이블에 없다

        assertThat(archiveService.getSeasonRanking(ROOM_ID, GAME_ID, SEASON))
            .extracting(RankingDto.GameRankingResponse::memberId)
            .containsExactly(1L);
    }

    // ── 시즌 목록 (경기 수는 시즌 경계로 자른다) ──

    @Test
    void 시즌_목록은_최신순이고_경기_수가_시즌_경계로_잘린다() {
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(boundaryService.zoneOfCommunity(1L)).thenReturn(ZoneId.of("Asia/Seoul"));
        when(snapshotRepository.findPlayerCountsByRoomIdAndBoardGameId(ROOM_ID, GAME_ID))
            .thenReturn(List.<Object[]>of(  // 쿼리는 최신순으로 준다
                new Object[]{"2026-08", 4L},
                new Object[]{"2026-07", 3L}));
        // 7월 시즌은 UTC 2026-06-30 15:00 ~ 07-31 15:00, 8월 시즌은 07-31 15:00 ~ 08-31 15:00
        when(matchRecordRepository.findPlayedAtByRoomIdAndBoardGameId(ROOM_ID, GAME_ID))
            .thenReturn(List.of(
                LocalDateTime.of(2026, 5, 2, 3, 0),    // 첫 시즌보다 앞 — 첫 시즌에 포함된다(하한 없음)
                LocalDateTime.of(2026, 7, 10, 3, 0),   // 7월
                LocalDateTime.of(2026, 7, 31, 14, 59), // 아직 7월 (한국시각 8/31 23:59)
                LocalDateTime.of(2026, 7, 31, 15, 0),  // 여기서부터 8월
                LocalDateTime.of(2026, 8, 20, 3, 0),   // 8월
                LocalDateTime.of(2026, 9, 5, 3, 0)));  // 마감 안 된 현재 시즌 — 어디에도 안 들어간다

        List<SeasonDto.RoomSeasonResponse> seasons = archiveService.getRoomSeasons(ROOM_ID, null);

        assertThat(seasons).extracting(SeasonDto.RoomSeasonResponse::seasonKey)
            .containsExactly("2026-08", "2026-07");
        assertThat(seasons).extracting(SeasonDto.RoomSeasonResponse::matchCount)
            .containsExactly(2, 3);
        assertThat(seasons).extracting(SeasonDto.RoomSeasonResponse::playerCount)
            .containsExactly(4, 3);
    }

    @Test
    void 마감된_시즌이_없으면_빈_목록이다() {
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(snapshotRepository.findPlayerCountsByRoomIdAndBoardGameId(ROOM_ID, GAME_ID))
            .thenReturn(List.of());

        assertThat(archiveService.getRoomSeasons(ROOM_ID, null)).isEmpty();
        verify(matchRecordRepository, never()).findPlayedAtByRoomIdAndBoardGameId(anyLong(), anyLong());
    }

    // ── ② 시상대 + 시상 조건 (§4) ──

    @Test
    void 시상대는_동점을_같은_순위로_두_칸_낸다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 5, 4);
        PlayerGameRating 지민 = rating(2L, "지민", 950.0, 5, 3);
        PlayerGameRating 현우 = rating(3L, "현우", 950.0, 5, 3); // 지민과 동점
        givenSnapshots(snapshot(1, 태윤), snapshot(2, 지민), snapshot(2, 현우));
        givenMembersExist(태윤, 지민, 현우);

        List<SeasonDto.PodiumEntry> podium = archiveService.getPodium(ROOM_ID, GAME_ID, SEASON);

        assertThat(podium).extracting(SeasonDto.PodiumEntry::rank).containsExactly(1, 2, 2);
        assertThat(podium).extracting(SeasonDto.PodiumEntry::nickname).containsExactly("태윤", "지민", "현우");
    }

    @Test
    void 참가자가_3명_미만이면_시상하지_않는다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 5, 4);
        PlayerGameRating 지민 = rating(2L, "지민", 950.0, 5, 1);
        givenSnapshots(snapshot(1, 태윤), snapshot(2, 지민));

        assertThat(archiveService.getPodium(ROOM_ID, GAME_ID, SEASON)).isEmpty();
    }

    @Test
    void 본인_3경기_미만이면_시상대에서_빠진다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 2, 2); // 2경기뿐
        PlayerGameRating 지민 = rating(2L, "지민", 950.0, 5, 3);
        PlayerGameRating 현우 = rating(3L, "현우", 550.0, 5, 1);
        givenSnapshots(snapshot(1, 태윤), snapshot(2, 지민), snapshot(3, 현우));
        givenMembersExist(지민, 현우);

        assertThat(archiveService.getPodium(ROOM_ID, GAME_ID, SEASON))
            .extracting(SeasonDto.PodiumEntry::nickname)
            .containsExactly("지민", "현우");
    }

    @Test
    void 시상대는_4위부터를_보지_않는다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 5, 4);
        PlayerGameRating 지민 = rating(2L, "지민", 1150.0, 5, 3);
        PlayerGameRating 현우 = rating(3L, "현우", 1050.0, 5, 2);
        PlayerGameRating 민서 = rating(4L, "민서", 950.0, 5, 1);
        givenSnapshots(snapshot(1, 태윤), snapshot(2, 지민), snapshot(3, 현우), snapshot(4, 민서));
        givenMembersExist(태윤, 지민, 현우, 민서);

        assertThat(archiveService.getPodium(ROOM_ID, GAME_ID, SEASON)).hasSize(3);
    }

    // ── ④ 내 점수 추이 ──

    @Test
    void 점수_추이는_과거순이고_게임을_안_주면_방의_게임을_쓴다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 5, 4);
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(snapshotRepository.findByMemberIdAndRoomIdAndBoardGameIdOrderBySeasonKeyAsc(1L, ROOM_ID, GAME_ID))
            .thenReturn(List.of(snapshot("2026-07", 3, 태윤), snapshot("2026-08", 1, 태윤)));

        List<SeasonDto.SeasonHistoryItem> history =
            archiveService.getMemberSeasonHistory(1L, ROOM_ID, null);

        assertThat(history).extracting(SeasonDto.SeasonHistoryItem::seasonKey)
            .containsExactly("2026-07", "2026-08");
        assertThat(history).extracting(SeasonDto.SeasonHistoryItem::rank).containsExactly(3, 1);
    }

    // ── 트로피 선반 ──

    @Test
    void 트로피는_참가자_3명_미만_시즌을_제외한다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 5, 4);
        when(snapshotRepository.findTrophiesByMemberId(1L))
            .thenReturn(List.of(snapshot("2026-08", 1, 태윤), snapshot("2026-07", 1, 태윤)));
        when(snapshotRepository.countParticipantsByRoomsAndSeasons(anyCollection(), anyCollection()))
            .thenReturn(List.<Object[]>of(
                new Object[]{"2026-08", ROOM_ID, GAME_ID, 4L},
                new Object[]{"2026-07", ROOM_ID, GAME_ID, 2L})); // 2명뿐이던 시즌
        givenRoomAndGameNamesExist();

        assertThat(archiveService.getTrophies(1L))
            .extracting(SeasonDto.TrophyResponse::seasonKey)
            .containsExactly("2026-08");
    }

    @Test
    void 트로피는_본인_3경기_미만_시즌을_제외한다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 2, 2);
        when(snapshotRepository.findTrophiesByMemberId(1L)).thenReturn(List.of(snapshot("2026-08", 1, 태윤)));

        assertThat(archiveService.getTrophies(1L)).isEmpty();
        verify(snapshotRepository, never()).countParticipantsByRoomsAndSeasons(anyCollection(), anyCollection());
    }

    @Test
    void 트로피는_삭제된_방의_행을_버린다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 5, 4);
        when(snapshotRepository.findTrophiesByMemberId(1L)).thenReturn(List.of(snapshot("2026-08", 1, 태윤)));
        when(snapshotRepository.countParticipantsByRoomsAndSeasons(anyCollection(), anyCollection()))
            .thenReturn(List.<Object[]>of(new Object[]{"2026-08", ROOM_ID, GAME_ID, 4L}));
        when(roomRepository.findAllById(anyCollection())).thenReturn(List.of()); // 방이 지워졌다
        when(boardGameRepository.findByIdIn(anyCollection())).thenReturn(List.of(카탄));

        assertThat(archiveService.getTrophies(1L)).isEmpty();
    }

    @Test
    void 트로피에는_방_이름과_게임_이름이_붙는다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 5, 4);
        when(snapshotRepository.findTrophiesByMemberId(1L)).thenReturn(List.of(snapshot("2026-08", 2, 태윤)));
        when(snapshotRepository.countParticipantsByRoomsAndSeasons(anyCollection(), anyCollection()))
            .thenReturn(List.<Object[]>of(new Object[]{"2026-08", ROOM_ID, GAME_ID, 4L}));
        givenRoomAndGameNamesExist();

        SeasonDto.TrophyResponse trophy = archiveService.getTrophies(1L).get(0);
        assertThat(trophy.roomName()).isEqualTo("금요모임");
        assertThat(trophy.boardGameName()).isEqualTo("카탄");
        assertThat(trophy.rank()).isEqualTo(2);
    }

    // ── 결산 카드용 커뮤니티 시상대 ──

    @Test
    void 커뮤니티_시상대는_한_사람의_가장_높은_행만_쓴다() {
        PlayerGameRating 태윤카탄 = rating(1L, "태윤", 1250.0, 5, 4);   // 1250
        PlayerGameRating 태윤아줄 = rating(1L, "태윤", 1150.0, 5, 3);   // 1150 — 같은 사람
        PlayerGameRating 지민 = rating(2L, "지민", 1100.0, 5, 3);       // 1100
        PlayerGameRating 현우 = rating(3L, "현우", 1050.0, 5, 2);       // 1050
        when(snapshotRepository.findBySeasonKeyAndRoomIds(anyCollection(), eq(SEASON)))
            .thenReturn(List.of(
                snapshot(1, 태윤카탄), snapshot(2, 태윤아줄), snapshot(1, 지민), snapshot(2, 현우)));
        givenMembersExist(태윤카탄, 지민, 현우);

        List<SeasonDto.PodiumEntry> podium =
            archiveService.getCommunityPodium(List.of(ROOM_ID, 200L), SEASON);

        assertThat(podium).extracting(SeasonDto.PodiumEntry::rank).containsExactly(1, 2, 3);
        assertThat(podium).extracting(SeasonDto.PodiumEntry::nickname)
            .containsExactly("태윤", "지민", "현우"); // 태윤이 두 칸을 먹지 않는다
        assertThat(podium.get(0).displayScore()).isCloseTo(1250.0, within(0.001)); // 낮은 행이 아니라 최고 행
    }

    @Test
    void 커뮤니티_시상대는_그_점수를_낸_방_이름을_싣는다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 5, 4);
        PlayerGameRating 지민 = rating(2L, "지민", 1100.0, 5, 3);
        PlayerGameRating 현우 = rating(3L, "현우", 1050.0, 5, 2);
        when(snapshotRepository.findBySeasonKeyAndRoomIds(anyCollection(), eq(SEASON)))
            .thenReturn(List.of(snapshot(1, 태윤), snapshot(2, 지민), snapshot(3, 현우)));
        givenMembersExist(태윤, 지민, 현우);
        when(roomRepository.findAllById(anyCollection())).thenReturn(List.of(room));

        assertThat(archiveService.getCommunityPodium(List.of(ROOM_ID), SEASON))
            .extracting(SeasonDto.PodiumEntry::roomName)
            .containsExactly("금요모임", "금요모임", "금요모임");
    }

    @Test
    void 커뮤니티_참가자가_3명_미만이면_결산에_시상대가_없다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 5, 4);
        PlayerGameRating 지민 = rating(2L, "지민", 1100.0, 5, 3);
        when(snapshotRepository.findBySeasonKeyAndRoomIds(anyCollection(), eq(SEASON)))
            .thenReturn(List.of(snapshot(1, 태윤), snapshot(2, 지민)));

        assertThat(archiveService.getCommunityPodium(List.of(ROOM_ID), SEASON)).isEmpty();
    }

    @Test
    void 커뮤니티_시상대도_본인_3경기_미만을_뺀다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1300.0, 2, 2);  // 2경기 — 1위지만 자격 없음
        PlayerGameRating 지민 = rating(2L, "지민", 1250.0, 5, 3);
        PlayerGameRating 현우 = rating(3L, "현우", 1100.0, 5, 2);
        PlayerGameRating 민서 = rating(4L, "민서", 1050.0, 5, 1);
        when(snapshotRepository.findBySeasonKeyAndRoomIds(anyCollection(), eq(SEASON)))
            .thenReturn(List.of(snapshot(1, 태윤), snapshot(2, 지민), snapshot(3, 현우), snapshot(4, 민서)));
        givenMembersExist(지민, 현우, 민서);

        assertThat(archiveService.getCommunityPodium(List.of(ROOM_ID), SEASON))
            .extracting(SeasonDto.PodiumEntry::nickname)
            .containsExactly("지민", "현우", "민서");
    }

    // ── fixtures ──

    private void givenSnapshots(SeasonRankSnapshot... snapshots) {
        when(snapshotRepository.findByRoomIdAndBoardGameIdAndSeasonKeyOrderByRankAsc(ROOM_ID, GAME_ID, SEASON))
            .thenReturn(List.of(snapshots));
    }

    private void givenMembersExist(PlayerGameRating... ratings) {
        List<Member> found = new ArrayList<>();
        for (PlayerGameRating rating : ratings) found.add(rating.getMember());
        when(memberRepository.findAllById(anyCollection())).thenReturn(found);
    }

    private void givenRoomAndGameNamesExist() {
        when(roomRepository.findAllById(anyCollection())).thenReturn(List.of(room));
        when(boardGameRepository.findByIdIn(anyCollection())).thenReturn(List.of(카탄));
    }

    private SeasonRankSnapshot snapshot(int rank, PlayerGameRating rating) {
        return SeasonRankSnapshot.from(SEASON, rank, rating);
    }

    private SeasonRankSnapshot snapshot(String seasonKey, int rank, PlayerGameRating rating) {
        return SeasonRankSnapshot.from(seasonKey, rank, rating);
    }

    private BoardGame boardGame(Long id, String name) {
        BoardGame game = BoardGame.builder().name(name).build();
        ReflectionTestUtils.setField(game, "id", id);
        return game;
    }

    private PlayerGameRating rating(
        Long memberId, String nickname, double score, int playCount, int winCount) {

        Member member = Member.builder().nickname(nickname).build();
        ReflectionTestUtils.setField(member, "id", memberId);

        PlayerGameRating rating = PlayerGameRating.builder()
            .member(member).boardGame(카탄).room(room).build();
        rating.getGameStats().update(score, 0.0, 0.0);
        for (int i = 0; i < playCount; i++) rating.addPlayCount();
        for (int i = 0; i < winCount; i++) rating.addWinCount();
        for (int i = 0; i < playCount - winCount; i++) rating.addLoseCount();
        return rating;
    }
}
