package com.board_game_back.Repository;

import com.board_game_back.Entity.SeasonRankSnapshot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeasonRankSnapshotRepository extends JpaRepository<SeasonRankSnapshot, Long> {

    /** 롤오버 멱등성 판단: 이 시즌·방의 스냅샷이 이미 있으면 다시 찍지 않는다. */
    boolean existsBySeasonKeyAndRoomId(String seasonKey, Long roomId);

    /** 커뮤니티에 마감된 시즌이 하나라도 있는지 — 시즌제 예고 배너를 언제 내릴지 판단한다 (§11). */
    boolean existsByRoomIdIn(Collection<Long> roomIds);

    /** 이 방이 마지막으로 끝낸 시즌. season_key가 'yyyy-MM'이라 문자열 MAX가 곧 최신이다. */
    @Query("SELECT MAX(s.seasonKey) FROM SeasonRankSnapshot s WHERE s.roomId = :roomId")
    String findLatestSeasonKeyByRoomId(@Param("roomId") Long roomId);

    /** 지난 시즌 순위표 (시즌 탭 ③) */
    List<SeasonRankSnapshot> findByRoomIdAndBoardGameIdAndSeasonKeyOrderByRankAsc(
        Long roomId, Long boardGameId, String seasonKey);

    /** 시상대 — 상위 3명만 필요하지만 동점 처리를 서비스에서 하므로 전체를 순위순으로 넘긴다. */
    List<SeasonRankSnapshot> findByRoomIdAndSeasonKeyOrderByRankAsc(Long roomId, String seasonKey);

    /** 시즌별 내 점수 추이 (시즌 탭 ④) */
    List<SeasonRankSnapshot> findByMemberIdAndRoomIdAndBoardGameIdOrderBySeasonKeyAsc(
        Long memberId, Long roomId, Long boardGameId);

    /** 프로필 트로피 선반 — 1~3위만 */
    @Query("""
        SELECT s FROM SeasonRankSnapshot s
        WHERE s.memberId = :memberId AND s.rank <= 3
        ORDER BY s.seasonKey DESC, s.rank ASC
        """)
    List<SeasonRankSnapshot> findTrophiesByMemberId(@Param("memberId") Long memberId);

    /** 랭킹 화면의 금관 — 특정 시즌 1위들 (동점 가능) */
    List<SeasonRankSnapshot> findByRoomIdAndBoardGameIdAndSeasonKeyAndRank(
        Long roomId, Long boardGameId, String seasonKey, int rank);

    /**
     * 방별 시즌 목록 (시즌 탭의 월 선택) — 시즌별 참가자 수.
     *
     * <p>경기 수는 여기서 낼 수 없다. 스냅샷은 사람당 한 행이라 {@code playCount}를 어떻게 합쳐도
     * 경기 수가 되지 않는다(한 경기에 여러 명이 들어간다). 경기 수는 시즌 경계로 자른
     * {@code match_record} 쪽에서 센다 — {@code SeasonArchiveService#getRoomSeasons}.
     */
    @Query("""
        SELECT s.seasonKey, COUNT(s)
        FROM SeasonRankSnapshot s
        WHERE s.roomId = :roomId AND s.boardGameId = :boardGameId
        GROUP BY s.seasonKey
        ORDER BY s.seasonKey DESC
        """)
    List<Object[]> findPlayerCountsByRoomIdAndBoardGameId(
        @Param("roomId") Long roomId, @Param("boardGameId") Long boardGameId);

    /**
     * 결산 카드 시상대 — 커뮤니티에 속한 방들의 한 시즌 스냅샷을 표시 점수 내림차순으로.
     * 방·게임 축이 섞이지만 단위가 같은 표시 점수라 "이번 시즌 우리 모임에서 제일 높았던 사람"은 정의된다.
     */
    @Query("""
        SELECT s FROM SeasonRankSnapshot s
        WHERE s.roomId IN :roomIds AND s.seasonKey = :seasonKey
        ORDER BY s.displayScore DESC
        """)
    List<SeasonRankSnapshot> findBySeasonKeyAndRoomIds(
        @Param("roomIds") Collection<Long> roomIds, @Param("seasonKey") String seasonKey);

    /**
     * 시상 자격 판단용 — (시즌, 방, 게임)별 참가자 수 (§4 트로피 인플레이션 가드).
     * 트로피 선반은 여러 방·시즌을 한 번에 그리므로 행마다 따로 세면 N+1이 된다.
     */
    @Query("""
        SELECT s.seasonKey, s.roomId, s.boardGameId, COUNT(s)
        FROM SeasonRankSnapshot s
        WHERE s.roomId IN :roomIds AND s.seasonKey IN :seasonKeys
        GROUP BY s.seasonKey, s.roomId, s.boardGameId
        """)
    List<Object[]> countParticipantsByRoomsAndSeasons(
        @Param("roomIds") Collection<Long> roomIds, @Param("seasonKeys") Collection<String> seasonKeys);
}
