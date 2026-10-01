package com.board_game_back.Service;

import com.board_game_back.Entity.EventName;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomSeason;
import com.board_game_back.Entity.SeasonRankSnapshot;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Repository.RoomSeasonRepository;
import com.board_game_back.Repository.SeasonRankSnapshotRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시즌 롤오버 — 리셋 직전 랭킹을 스냅샷으로 남기고, 방별 점수를 초기값으로 되돌리고,
 * 같은 길이의 다음 시즌을 연다. 기획: {@code docs/plans/plan-season-reset.md} §5, §22.
 *
 * <p><b>멱등하다.</b> 이미 닫힌 시즌은 다시 닫지 않고, 스냅샷은 시즌당 한 번만 찍는다.
 * 스케줄러·경기 등록 직전 검사·관리자 수동 트리거가 같은 시즌을 겹쳐 부를 수 있다.
 *
 * <p><b>전체 랭킹은 건드리지 않는다</b> (§2). 리셋 대상은 {@code PlayerGameRating.gameStats}뿐이고
 * {@code Member.overallStats}·{@code bestDisplayScore}는 누적 경력이라 그대로 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonRolloverService {

    private final RoomRepository roomRepository;
    private final RoomSeasonRepository seasonRepository;
    private final PlayerGameRatingRepository ratingRepository;
    private final SeasonRankSnapshotRepository snapshotRepository;
    private final SeasonBoundaryService boundaryService;
    private final UserEventService userEventService;

    /**
     * 경기 등록 직전에 부른다. 스케줄러는 매시 정각에만 돌기 때문에 이게 없으면 종료 후 최대 1시간 동안
     * 등록된 경기가 지난 시즌 점수에 섞인다.
     */
    @Transactional
    public void rolloverIfDue(Long roomId) {
        LocalDateTime now = SeasonBoundaryService.nowUtc();
        seasonRepository.findByRoomIdAndClosedAtIsNull(roomId)
            .filter(season -> season.isDue(now))
            .ifPresent(season -> rollover(season, now));
    }

    /** @return 다음 시즌. 방이 삭제됐으면 시즌만 닫고 빈 값. */
    @Transactional
    public Optional<RoomSeason> rollover(Long roomSeasonId) {
        RoomSeason season = seasonRepository.findById(roomSeasonId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 시즌입니다."));
        LocalDateTime now = SeasonBoundaryService.nowUtc();
        if (!season.isDue(now)) return Optional.empty();
        return rollover(season, now);
    }

    private Optional<RoomSeason> rollover(RoomSeason season, LocalDateTime now) {
        Optional<Room> room = roomRepository.findById(season.getRoomId());

        int playerCount = room.map(r -> snapshotAndReset(r, season)).orElse(0);

        // 진행 중 시즌은 방당 하나(uq_room_season_open)라 닫힌 상태를 먼저 내보내야 다음 시즌을 넣을 수 있다.
        season.close(now);
        seasonRepository.saveAndFlush(season);

        if (room.isEmpty()) return Optional.empty();

        RoomSeason next = seasonRepository.save(season.next(boundaryService.zoneOfRoom(season.getRoomId()), now));

        log.info("시즌 롤오버 room={} season#{} → #{} 대상={}명",
            season.getRoomId(), season.getSeasonNumber(), next.getSeasonNumber(), playerCount);

        // 리셋이 리텐션을 올렸는지 판정할 때 이 이벤트가 분모가 된다 (§10). 경기 없는 방은 남길 사건이 없다.
        if (playerCount > 0) {
            Map<String, Object> props = new HashMap<>();
            props.put("room_id", season.getRoomId());
            props.put("room_season_id", season.getId());
            props.put("season_number", season.getSeasonNumber());
            props.put("playerCount", playerCount);
            userEventService.recordServerSide(EventName.SEASON_ROLLED_OVER, room.get().getCommunityId(), props);
        }
        return Optional.of(next);
    }

    /** @return 스냅샷에 들어간 사람 수 (게임이 여러 개면 중복 포함) */
    private int snapshotAndReset(Room room, RoomSeason season) {
        if (snapshotRepository.existsByRoomSeasonId(season.getId())) return 0;

        List<PlayerGameRating> ratings = ratingRepository.findByRoomIdWithMemberAndBoardGame(room.getId());
        List<SeasonRankSnapshot> snapshots = new ArrayList<>();
        for (List<PlayerGameRating> perGame : groupByBoardGame(ratings).values()) {
            snapshots.addAll(snapshotGame(season.getId(), perGame));
        }

        // 참가자가 없으면 스냅샷도 리셋도 의미가 없다. 이미 초기값인 방을 건드리지 않는다.
        if (snapshots.isEmpty()) return 0;

        snapshotRepository.saveAll(snapshots);
        ratings.forEach(PlayerGameRating::reset);
        ratingRepository.saveAll(ratings);
        return snapshots.size();
    }

    /** 한 게임의 최종 순위를 사진으로 남긴다. 동점은 같은 순위를 받는다 (1,2,2,4). */
    private List<SeasonRankSnapshot> snapshotGame(Long roomSeasonId, List<PlayerGameRating> perGame) {
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
            snapshots.add(SeasonRankSnapshot.from(roomSeasonId, rank, rating));
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
