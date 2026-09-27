package com.board_game_back.Service;

import com.board_game_back.Entity.EventName;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.SeasonRankSnapshot;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Repository.SeasonRankSnapshotRepository;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시즌 롤오버 — 리셋 직전 랭킹을 스냅샷으로 남기고 방별 점수를 초기값으로 되돌린다.
 * 기획: {@code docs/plans/plan-season-reset.md} §5.
 *
 * <p><b>멱등하다.</b> 같은 (시즌, 방)을 두 번 돌려도 두 번 박히지 않는다. Railway 재배포나
 * 중복 스케줄로 여러 번 불릴 수 있고, 관리자가 수동으로 또 누를 수도 있기 때문이다.
 *
 * <p><b>전체 랭킹은 건드리지 않는다</b> (§2). 리셋 대상은 {@code PlayerGameRating.gameStats}뿐이고
 * {@code Member.overallStats}·{@code bestDisplayScore}는 누적 경력이라 그대로 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonRolloverService {

    private final CommunityRepository communityRepository;
    private final RoomRepository roomRepository;
    private final PlayerGameRatingRepository ratingRepository;
    private final SeasonRankSnapshotRepository snapshotRepository;
    private final UserEventService userEventService;

    /**
     * 커뮤니티의 한 시즌을 마감한다.
     *
     * @param season 마감할 시즌 (끝난 달). 이 달의 다음 달 1일 00:00이 경계다.
     * @return 실제로 마감된 방 수 (이미 마감됐거나 찍을 사진이 없는 방은 제외)
     */
    @Transactional
    public int rollover(Long communityId, YearMonth season) {
        communityRepository.findById(communityId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 커뮤니티입니다."));

        String seasonKey = season.toString();

        List<Room> rooms = roomRepository.findByCommunityId(communityId);
        if (rooms.isEmpty()) return 0;

        int rolled = 0;
        Set<Long> affectedMembers = new HashSet<>();
        for (Room room : rooms) {
            List<SeasonRankSnapshot> written = rolloverRoom(room, seasonKey);
            if (written.isEmpty()) continue;
            rolled++;
            written.forEach(snapshot -> affectedMembers.add(snapshot.getMemberId()));
        }

        log.info("시즌 롤오버 community={} season={} 마감된 방={}/{} 대상={}명",
            communityId, seasonKey, rolled, rooms.size(), affectedMembers.size());

        // 리셋이 리텐션을 올렸는지 판정할 때 이 이벤트가 분모가 된다 (§10).
        // 한 판도 마감되지 않았으면(멱등 재실행·경기 없는 방) 남길 사건이 없다.
        if (rolled > 0) {
            userEventService.recordServerSide(EventName.SEASON_ROLLED_OVER, communityId,
                Map.of("season_key", seasonKey, "playerCount", affectedMembers.size()));
        }
        return rolled;
    }

    /** @return 실제로 쓴 스냅샷. 비어 있으면 이 방은 마감되지 않았다. */
    private List<SeasonRankSnapshot> rolloverRoom(Room room, String seasonKey) {
        Long roomId = room.getId();

        // 멱등성: 이 방의 이 시즌은 이미 마감됐다.
        if (snapshotRepository.existsBySeasonKeyAndRoomId(seasonKey, roomId)) return List.of();

        List<PlayerGameRating> ratings = ratingRepository.findByRoomIdWithMemberAndBoardGame(roomId);
        List<SeasonRankSnapshot> snapshots = new ArrayList<>();
        for (List<PlayerGameRating> perGame : groupByBoardGame(ratings).values()) {
            snapshots.addAll(snapshotGame(seasonKey, perGame));
        }

        // 참가자가 없으면 스냅샷도 리셋도 의미가 없다. 이미 초기값인 방을 건드리지 않는다.
        if (snapshots.isEmpty()) return List.of();

        snapshotRepository.saveAll(snapshots);
        ratings.forEach(PlayerGameRating::reset);
        ratingRepository.saveAll(ratings);
        return snapshots;
    }

    /** 한 게임의 최종 순위를 사진으로 남긴다. 동점은 같은 순위를 받는다 (1,2,2,4). */
    private List<SeasonRankSnapshot> snapshotGame(String seasonKey, List<PlayerGameRating> perGame) {
        List<PlayerGameRating> played = perGame.stream()
            .filter(r -> r.getPlayCount() > 0)
            .sorted(Comparator.comparingDouble((PlayerGameRating r) -> r.getGameStats().getDisplayScore()).reversed())
            .toList();

        List<SeasonRankSnapshot> snapshots = new ArrayList<>();
        int rank = 0;
        double previousScore = Double.NaN;
        for (int i = 0; i < played.size(); i++) {
            PlayerGameRating rating = played.get(i);
            double score = rating.getGameStats().getDisplayScore();
            if (score != previousScore) {
                rank = i + 1;
                previousScore = score;
            }
            snapshots.add(SeasonRankSnapshot.from(seasonKey, rank, rating));
        }
        return snapshots;
    }

    private Map<Long, List<PlayerGameRating>> groupByBoardGame(List<PlayerGameRating> ratings) {
        Map<Long, List<PlayerGameRating>> byGame = new LinkedHashMap<>();
        for (PlayerGameRating rating : ratings) {
            byGame.computeIfAbsent(rating.getBoardGame().getId(), id -> new ArrayList<>()).add(rating);
        }
        return byGame;
    }
}
