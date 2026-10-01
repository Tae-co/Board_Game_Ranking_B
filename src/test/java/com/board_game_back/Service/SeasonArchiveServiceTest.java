package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.RankingDto;
import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomSeason;
import com.board_game_back.Entity.SeasonRankSnapshot;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Repository.RoomSeasonRepository;
import com.board_game_back.Repository.SeasonRankSnapshotRepository;
import java.time.LocalDate;
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
    private static final ZoneId 서울 = ZoneId.of("Asia/Seoul");
    /** 시즌 1: ~ 2026-07-31(KST), 시즌 2: 2026-08-01 ~ 08-31(KST). 둘 다 마감됐다. */
    private static final Long SEASON_1 = 7L;
    private static final Long SEASON_2 = 8L;
    private static final Long SEASON = SEASON_2;

    @Mock private SeasonRankSnapshotRepository snapshotRepository;
    @Mock private RoomSeasonRepository seasonRepository;
    @Mock private MatchRecordRepository matchRecordRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private BoardGameRepository boardGameRepository;
    @Mock private SeasonBoundaryService boundaryService;

    @InjectMocks private SeasonArchiveService archiveService;

    private Room room;
    private RoomSeason 시즌1;
    private RoomSeason 시즌2;
    private BoardGame 카탄;

    @BeforeEach
    void setUp() {
        room = new Room("금요모임", "ABC123", GAME_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        room.assignCommunity(1L);

        카탄 = boardGame(GAME_ID, "카탄");
        시즌1 = closedSeason(SEASON_1, 1, null,
            LocalDateTime.of(2026, 5, 2, 3, 0), LocalDate.of(2026, 7, 31));
        시즌2 = closedSeason(SEASON_2, 2, "여름 리그",
            시즌1.getEndAt(), LocalDate.of(2026, 8, 31));
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
    void 다른_방의_시즌은_조회하지_않는다() {
        RoomSeason 남의시즌 = closedSeason(99L, 1, null, LocalDateTime.of(2026, 5, 1, 0, 0), LocalDate.of(2026, 7, 31));
        ReflectionTestUtils.setField(남의시즌, "roomId", 555L);
        when(seasonRepository.findById(99L)).thenReturn(Optional.of(남의시즌));

        assertThat(archiveService.getSeasonRanking(ROOM_ID, GAME_ID, 99L)).isEmpty();
        verify(snapshotRepository, never()).findByRoomSeasonIdAndBoardGameIdOrderByRankAsc(anyLong(), anyLong());
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
    void 시즌_목록은_최신순이고_경기_수가_시즌_기간으로_잘린다() {
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(boundaryService.zoneOfRoom(ROOM_ID)).thenReturn(서울);
        when(snapshotRepository.findPlayerCountsByRoomIdAndBoardGameId(ROOM_ID, GAME_ID))
            .thenReturn(List.<Object[]>of(
                new Object[]{SEASON_1, "2026-07", 3L},
                new Object[]{SEASON_2, null, 4L}));
        when(seasonRepository.findByRoomIdAndClosedAtIsNotNullOrderBySeasonNumberDesc(ROOM_ID))
            .thenReturn(List.of(시즌2, 시즌1));
        // 시즌 1은 UTC ~ 07-31 15:00, 시즌 2는 07-31 15:00 ~ 08-31 15:00
        when(matchRecordRepository.findPlayedAtByRoomIdAndBoardGameId(ROOM_ID, GAME_ID))
            .thenReturn(List.of(
                LocalDateTime.of(2026, 5, 1, 3, 0),    // 첫 시즌 시작보다 앞 — 첫 시즌에 포함된다(하한 없음)
                LocalDateTime.of(2026, 7, 10, 3, 0),   // 시즌 1
                LocalDateTime.of(2026, 7, 31, 14, 59), // 아직 시즌 1 (한국시각 7/31 23:59)
                LocalDateTime.of(2026, 7, 31, 15, 0),  // 여기서부터 시즌 2
                LocalDateTime.of(2026, 8, 20, 3, 0),   // 시즌 2
                LocalDateTime.of(2026, 9, 5, 3, 0)));  // 마감 안 된 현재 시즌 — 어디에도 안 들어간다

        List<SeasonDto.RoomSeasonResponse> seasons = archiveService.getRoomSeasons(ROOM_ID, null);

        assertThat(seasons).extracting(SeasonDto.RoomSeasonResponse::seasonId).containsExactly(SEASON_2, SEASON_1);
        assertThat(seasons).extracting(SeasonDto.RoomSeasonResponse::name).containsExactly("여름 리그", null);
        assertThat(seasons).extracting(SeasonDto.RoomSeasonResponse::seasonKey).containsExactly(null, "2026-07");
        assertThat(seasons).extracting(SeasonDto.RoomSeasonResponse::matchCount).containsExactly(2, 3);
        assertThat(seasons).extracting(SeasonDto.RoomSeasonResponse::playerCount).containsExactly(4, 3);
        assertThat(seasons.get(0).endDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void 이_게임_기록이_없는_시즌은_목록에_없다() {
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(boundaryService.zoneOfRoom(ROOM_ID)).thenReturn(서울);
        when(snapshotRepository.findPlayerCountsByRoomIdAndBoardGameId(ROOM_ID, GAME_ID))
            .thenReturn(List.<Object[]>of(new Object[]{SEASON_1, null, 3L}));
        when(seasonRepository.findByRoomIdAndClosedAtIsNotNullOrderBySeasonNumberDesc(ROOM_ID))
            .thenReturn(List.of(시즌2, 시즌1)); // 시즌 2는 경기 없이 넘어갔다

        assertThat(archiveService.getRoomSeasons(ROOM_ID, null))
            .extracting(SeasonDto.RoomSeasonResponse::seasonId).containsExactly(SEASON_1);
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
        when(snapshotRepository.findByMemberIdAndRoomIdAndBoardGameIdOrderByRoomSeasonIdAsc(1L, ROOM_ID, GAME_ID))
            .thenReturn(List.of(snapshot(SEASON_1, 3, 태윤), snapshot(SEASON_2, 1, 태윤)));
        givenSeasonsExist();

        List<SeasonDto.SeasonHistoryItem> history =
            archiveService.getMemberSeasonHistory(1L, ROOM_ID, null);

        assertThat(history).extracting(SeasonDto.SeasonHistoryItem::seasonNumber).containsExactly(1, 2);
        assertThat(history).extracting(SeasonDto.SeasonHistoryItem::seasonName).containsExactly(null, "여름 리그");
        assertThat(history).extracting(SeasonDto.SeasonHistoryItem::rank).containsExactly(3, 1);
    }

    // ── 트로피 선반 ──

    @Test
    void 트로피는_참가자_3명_미만_시즌을_제외한다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 5, 4);
        when(snapshotRepository.findTrophiesByMemberId(1L))
            .thenReturn(List.of(snapshot(SEASON_2, 1, 태윤), snapshot(SEASON_1, 1, 태윤)));
        givenSeasonsExist();
        when(snapshotRepository.countParticipantsBySeasons(anyCollection()))
            .thenReturn(List.<Object[]>of(
                new Object[]{SEASON_2, GAME_ID, 4L},
                new Object[]{SEASON_1, GAME_ID, 2L})); // 2명뿐이던 시즌
        givenRoomAndGameNamesExist();

        assertThat(archiveService.getTrophies(1L))
            .extracting(SeasonDto.TrophyResponse::seasonId)
            .containsExactly(SEASON_2);
    }

    @Test
    void 트로피는_본인_3경기_미만_시즌을_제외한다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 2, 2);
        when(snapshotRepository.findTrophiesByMemberId(1L)).thenReturn(List.of(snapshot(SEASON_2, 1, 태윤)));

        assertThat(archiveService.getTrophies(1L)).isEmpty();
        verify(snapshotRepository, never()).countParticipantsBySeasons(anyCollection());
    }

    @Test
    void 트로피는_삭제된_방의_행을_버린다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 5, 4);
        when(snapshotRepository.findTrophiesByMemberId(1L)).thenReturn(List.of(snapshot(SEASON_2, 1, 태윤)));
        givenSeasonsExist();
        when(snapshotRepository.countParticipantsBySeasons(anyCollection()))
            .thenReturn(List.<Object[]>of(new Object[]{SEASON_2, GAME_ID, 4L}));
        when(roomRepository.findAllById(anyCollection())).thenReturn(List.of()); // 방이 지워졌다
        when(boardGameRepository.findByIdIn(anyCollection())).thenReturn(List.of(카탄));

        assertThat(archiveService.getTrophies(1L)).isEmpty();
    }

    @Test
    void 트로피에는_시즌_이름과_방_이름과_게임_이름이_붙는다() {
        PlayerGameRating 태윤 = rating(1L, "태윤", 1250.0, 5, 4);
        when(snapshotRepository.findTrophiesByMemberId(1L)).thenReturn(List.of(snapshot(SEASON_2, 2, 태윤)));
        givenSeasonsExist();
        when(snapshotRepository.countParticipantsBySeasons(anyCollection()))
            .thenReturn(List.<Object[]>of(new Object[]{SEASON_2, GAME_ID, 4L}));
        givenRoomAndGameNamesExist();

        SeasonDto.TrophyResponse trophy = archiveService.getTrophies(1L).get(0);
        assertThat(trophy.seasonName()).isEqualTo("여름 리그");
        assertThat(trophy.roomName()).isEqualTo("금요모임");
        assertThat(trophy.boardGameName()).isEqualTo("카탄");
        assertThat(trophy.rank()).isEqualTo(2);
    }

    // ── fixtures ──

    private void givenSnapshots(SeasonRankSnapshot... snapshots) {
        when(seasonRepository.findById(SEASON)).thenReturn(Optional.of(시즌2));
        when(snapshotRepository.findByRoomSeasonIdAndBoardGameIdOrderByRankAsc(SEASON, GAME_ID))
            .thenReturn(List.of(snapshots));
    }

    private void givenSeasonsExist() {
        when(seasonRepository.findByIdIn(anyCollection())).thenReturn(List.of(시즌1, 시즌2));
    }

    private RoomSeason closedSeason(Long id, int number, String name, LocalDateTime startAt, LocalDate endDate) {
        RoomSeason season = RoomSeason.open(ROOM_ID, number, name, startAt, endDate, 서울);
        ReflectionTestUtils.setField(season, "id", id);
        season.close(season.getEndAt());
        return season;
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

    private SeasonRankSnapshot snapshot(Long seasonId, int rank, PlayerGameRating rating) {
        return SeasonRankSnapshot.from(seasonId, rank, rating);
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
