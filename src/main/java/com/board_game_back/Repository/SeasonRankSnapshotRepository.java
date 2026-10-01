package com.board_game_back.Repository;

import com.board_game_back.Entity.SeasonRankSnapshot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeasonRankSnapshotRepository extends JpaRepository<SeasonRankSnapshot, Long> {

    /** 롤오버 멱등성 판단: 이 시즌의 스냅샷이 이미 있으면 다시 찍지 않는다. */
    boolean existsByRoomSeasonId(Long roomSeasonId);

    /** 지난 시즌 순위표 (시즌 탭 ③) · 시상대 */
    List<SeasonRankSnapshot> findByRoomSeasonIdAndBoardGameIdOrderByRankAsc(Long roomSeasonId, Long boardGameId);

    /** 시즌별 내 점수 추이 (시즌 탭 ④). 시즌 id는 방 안에서 시즌 번호 순으로 증가한다. */
    List<SeasonRankSnapshot> findByMemberIdAndRoomIdAndBoardGameIdOrderByRoomSeasonIdAsc(
        Long memberId, Long roomId, Long boardGameId);

    /** 프로필 트로피 선반 — 1~3위만 */
    @Query("""
        SELECT s FROM SeasonRankSnapshot s
        WHERE s.memberId = :memberId AND s.rank <= 3
        ORDER BY s.roomSeasonId DESC, s.rank ASC
        """)
    List<SeasonRankSnapshot> findTrophiesByMemberId(@Param("memberId") Long memberId);

    /**
     * 방별 시즌 목록 — 시즌별 참가자 수. {@code seasonKey}는 시즌 안에서 같은 값이라 묶어도 행이 늘지 않는다.
     *
     * <p>경기 수는 여기서 낼 수 없다. 스냅샷은 사람당 한 행이라 {@code playCount}를 어떻게 합쳐도
     * 경기 수가 되지 않는다(한 경기에 여러 명이 들어간다). 경기 수는 시즌 경계로 자른
     * {@code match_record} 쪽에서 센다 — {@code SeasonArchiveService#getRoomSeasons}.
     */
    @Query("""
        SELECT s.roomSeasonId, s.seasonKey, COUNT(s)
        FROM SeasonRankSnapshot s
        WHERE s.roomId = :roomId AND s.boardGameId = :boardGameId
        GROUP BY s.roomSeasonId, s.seasonKey
        """)
    List<Object[]> findPlayerCountsByRoomIdAndBoardGameId(
        @Param("roomId") Long roomId, @Param("boardGameId") Long boardGameId);

    /**
     * 시상 자격 판단용 — (시즌, 게임)별 참가자 수 (§4 트로피 인플레이션 가드).
     * 트로피 선반은 여러 방·시즌을 한 번에 그리므로 행마다 따로 세면 N+1이 된다.
     */
    @Query("""
        SELECT s.roomSeasonId, s.boardGameId, COUNT(s)
        FROM SeasonRankSnapshot s
        WHERE s.roomSeasonId IN :roomSeasonIds
        GROUP BY s.roomSeasonId, s.boardGameId
        """)
    List<Object[]> countParticipantsBySeasons(@Param("roomSeasonIds") Collection<Long> roomSeasonIds);
}
