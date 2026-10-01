package com.board_game_back.Repository;

import com.board_game_back.Entity.MatchParticipant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

    @Modifying
    @Query("DELETE FROM MatchParticipant mp WHERE mp.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);

    // ── decay 폭의 근거 (기획 §7): 승리 1회당 평균 표시점수 상승 ──
    // ratingChange는 이미 표시 점수 단위 델타다. 전 기간 누적이고 시즌으로 자르지 않는다 —
    // 시즌 안에서만 평균을 내면 리셋 직후엔 표본이 0이라 깎을 근거가 사라진다.

    /** 사람×방×게임 승리 평균. 사람마다 다른 폭으로 깎기 위한 값이다. */
    @Query("""
        SELECT mp.matchRecord.room.id, mp.matchRecord.boardGame.id, mp.member.id, AVG(mp.ratingChange)
        FROM MatchParticipant mp
        WHERE mp.placement = 1 AND mp.matchRecord.room IS NOT NULL
        GROUP BY mp.matchRecord.room.id, mp.matchRecord.boardGame.id, mp.member.id
        """)
    List<Object[]> findWinRatingChangeAvgByPlayer();

    /** 방×게임 승리 평균. 승리가 0회인 사람에게 대신 적용한다. */
    @Query("""
        SELECT mp.matchRecord.room.id, mp.matchRecord.boardGame.id, AVG(mp.ratingChange)
        FROM MatchParticipant mp
        WHERE mp.placement = 1 AND mp.matchRecord.room IS NOT NULL
        GROUP BY mp.matchRecord.room.id, mp.matchRecord.boardGame.id
        """)
    List<Object[]> findWinRatingChangeAvgByRoomGame();

    // ── 랭킹 표의 순위 분포 ──
    // 승/패만으로는 "2등 15번"과 "꼴등 15번"이 같은 15패로 보인다. 점수는 순위 전체를 반영하니
    // 순위별 횟수를 같이 보여줘야 점수가 납득된다.

    @Query("""
        SELECT mp.member.id, mp.placement, COUNT(mp)
        FROM MatchParticipant mp
        WHERE mp.matchRecord.room.id = :roomId AND mp.matchRecord.boardGame.id = :boardGameId
          AND mp.matchRecord.playedAt >= :from AND mp.matchRecord.playedAt < :to
        GROUP BY mp.member.id, mp.placement
        """)
    List<Object[]> countPlacementsByMember(
        @Param("roomId") Long roomId, @Param("boardGameId") Long boardGameId,
        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 멤버별 순위 횟수. 인덱스 0 = 1등. from·to가 null이면 그쪽 경계 없음.
     * 모든 목록을 이 범위의 최하위 순위까지 0으로 채운다 — 프론트는 한 페이지씩 받으므로
     * 페이지마다 칸 수가 달라지지 않게 서버가 길이를 맞춘다.
     */
    default Map<Long, List<Integer>> placementCountsByMember(
        Long roomId, Long boardGameId, LocalDateTime from, LocalDateTime to) {
        Map<Long, List<Integer>> counts = new HashMap<>();
        for (Object[] row : countPlacementsByMember(roomId, boardGameId,
                from != null ? from : LocalDateTime.of(1970, 1, 1, 0, 0),
                to != null ? to : LocalDateTime.of(9999, 1, 1, 0, 0))) {
            int placement = ((Number) row[1]).intValue();
            if (placement < 1) continue;
            List<Integer> list = counts.computeIfAbsent((Long) row[0], id -> new ArrayList<>());
            while (list.size() < placement) list.add(0);
            list.set(placement - 1, ((Number) row[2]).intValue());
        }
        int maxPlace = counts.values().stream().mapToInt(List::size).max().orElse(0);
        for (List<Integer> list : counts.values()) {
            while (list.size() < maxPlace) list.add(0);
        }
        return counts;
    }
}
