package com.board_game_back.Service;

import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.SeasonRankSnapshot;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Repository.SeasonRankSnapshotRepository;
import com.board_game_back.Utils.RegionTimeZones;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** 방의 첫 시즌이 이보다 짧으면 롤오버를 건너뛰고 다음 달에 합친다 (§1). */
    private static final int MIN_FIRST_SEASON_DAYS = 14;

    private final CommunityRepository communityRepository;
    private final RoomRepository roomRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final PlayerGameRatingRepository ratingRepository;
    private final SeasonRankSnapshotRepository snapshotRepository;

    /**
     * 커뮤니티의 한 시즌을 마감한다.
     *
     * @param season 마감할 시즌 (끝난 달). 이 달의 다음 달 1일 00:00이 경계다.
     * @return 실제로 마감된 방 수 (이미 마감됐거나 14일 규칙으로 이월된 방은 제외)
     */
    @Transactional
    public int rollover(Long communityId, YearMonth season) {
        Community community = communityRepository.findById(communityId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 커뮤니티입니다."));

        ZoneId zone = RegionTimeZones.of(community.getRegion());
        String seasonKey = season.toString();
        LocalDateTime seasonEndUtc = SeasonBoundaryService.startOfSeasonUtc(zone, season.plusMonths(1));

        List<Room> rooms = roomRepository.findByCommunityId(communityId);
        if (rooms.isEmpty()) return 0;

        Map<Long, LocalDateTime> firstPlayedByRoom = firstPlayedAtByRoom(rooms);

        int rolled = 0;
        for (Room room : rooms) {
            if (rolloverRoom(room, seasonKey, seasonEndUtc, firstPlayedByRoom.get(room.getId()))) rolled++;
        }

        log.info("시즌 롤오버 community={} season={} 마감된 방={}/{}", communityId, seasonKey, rolled, rooms.size());
        return rolled;
    }

    private boolean rolloverRoom(
        Room room, String seasonKey, LocalDateTime seasonEndUtc, LocalDateTime firstPlayedAt) {

        Long roomId = room.getId();

        // 멱등성: 이 방의 이 시즌은 이미 마감됐다.
        if (snapshotRepository.existsBySeasonKeyAndRoomId(seasonKey, roomId)) return false;

        // 14일 규칙은 첫 시즌에만 적용한다. 한 번이라도 마감한 방은 이미 매월 주기에 올라탔다.
        if (!snapshotRepository.existsByRoomId(roomId)) {
            if (firstPlayedAt == null) return false; // 경기가 없으면 찍을 사진도 없다
            if (Duration.between(firstPlayedAt, seasonEndUtc).toDays() < MIN_FIRST_SEASON_DAYS) return false;
        }

        List<PlayerGameRating> ratings = ratingRepository.findByRoomIdWithMemberAndBoardGame(roomId);
        List<SeasonRankSnapshot> snapshots = new ArrayList<>();
        for (List<PlayerGameRating> perGame : groupByBoardGame(ratings).values()) {
            snapshots.addAll(snapshotGame(seasonKey, perGame));
        }

        // 참가자가 없으면 스냅샷도 리셋도 의미가 없다. 이미 초기값인 방을 건드리지 않는다.
        if (snapshots.isEmpty()) return false;

        snapshotRepository.saveAll(snapshots);
        ratings.forEach(PlayerGameRating::reset);
        ratingRepository.saveAll(ratings);
        return true;
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

    private Map<Long, LocalDateTime> firstPlayedAtByRoom(List<Room> rooms) {
        List<Long> roomIds = rooms.stream().map(Room::getId).toList();
        Map<Long, LocalDateTime> firstPlayed = new HashMap<>();
        for (Object[] row : matchRecordRepository.findFirstPlayedAtByRoom(roomIds)) {
            firstPlayed.put((Long) row[0], (LocalDateTime) row[1]);
        }
        return firstPlayed;
    }
}
