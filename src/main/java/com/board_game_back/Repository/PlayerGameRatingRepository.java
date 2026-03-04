package com.board_game_back.Repository;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerGameRatingRepository extends JpaRepository<PlayerGameRating, Long> {

    // --- [기존: 앱 전체 랭킹용] ---
    Optional<PlayerGameRating> findByMemberAndBoardGame(Member member, BoardGame boardGame);
    List<PlayerGameRating> findByBoardGameIdOrderByGameStatsRatingDesc(Long boardGameId);

    // --- [신규: 방(Group)별 랭킹용] ---

    // 1. 특정 방, 특정 유저가 특정 게임에서 가진 랭킹 정보 찾기 (방별 점수 업데이트용)
    Optional<PlayerGameRating> findByMemberAndBoardGameAndRoom(Member member, BoardGame boardGame, Room room);

    // 2. 특정 방 내에서의 특정 게임 리더보드 가져오기 (방 내부 순위 정렬)
    List<PlayerGameRating> findByRoomIdAndBoardGameIdOrderByGameStatsRatingDesc(Long roomId, Long boardGameId);

    // 3. 특정 방 안에서 플레이 횟수가 N번 이상인 사람만 랭킹에 노출
    List<PlayerGameRating> findByRoomIdAndBoardGameIdAndPlayCountGreaterThanEqualOrderByGameStatsRatingDesc(
        Long roomId, Long boardGameId, int minPlayCount);

}
