package com.board_game_back.Repository;

import com.board_game_back.Entity.SeasonRankSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeasonRankSnapshotRepository extends JpaRepository<SeasonRankSnapshot, Long> {

    /** 롤오버 멱등성 판단: 이 시즌·방의 스냅샷이 이미 있으면 다시 찍지 않는다. */
    boolean existsBySeasonKeyAndRoomId(String seasonKey, Long roomId);

    /** 이 방이 한 번이라도 롤오버된 적 있는지 — 없으면 아직 첫 시즌이다(14일 규칙 대상). */
    boolean existsByRoomId(Long roomId);

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

    /** 방별 시즌 목록 (시즌 탭의 월 선택) */
    @Query("""
        SELECT s.seasonKey, COUNT(s), MAX(s.playCount)
        FROM SeasonRankSnapshot s
        WHERE s.roomId = :roomId
        GROUP BY s.seasonKey
        ORDER BY s.seasonKey DESC
        """)
    List<Object[]> findSeasonSummariesByRoomId(@Param("roomId") Long roomId);
}
