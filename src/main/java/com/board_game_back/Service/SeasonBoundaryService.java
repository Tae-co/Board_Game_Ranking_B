package com.board_game_back.Service;

import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Repository.SeasonRankSnapshotRepository;
import com.board_game_back.Utils.RegionTimeZones;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시즌 경계를 계산하는 한 곳. 롤오버와 재계산이 서로 다른 경계를 쓰면
 * "리셋했는데 재계산 한 번에 지난 시즌이 부활한다"가 되므로 두 쪽이 이 클래스만 본다.
 *
 * <p><b>타임존:</b> {@code MatchRecord.playedAt}은 UTC 벽시계인데 시즌 경계는 커뮤니티
 * {@code region} 타임존의 1일 00:00이다. 그래서 경계는 항상 zone에서 만든 뒤 UTC로 옮겨
 * 돌려준다 — 한국 커뮤니티의 10월 시즌은 UTC로 9월 30일 15:00에 시작한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonBoundaryService {

    private final RoomRepository roomRepository;
    private final CommunityRepository communityRepository;
    private final SeasonRankSnapshotRepository snapshotRepository;

    /** zone 기준 {@code month} 1일 00:00을 UTC 벽시계로. playedAt과 직접 비교할 수 있는 값이다. */
    public static LocalDateTime startOfSeasonUtc(ZoneId zone, YearMonth month) {
        return LocalDateTime.ofInstant(month.atDay(1).atStartOfDay(zone).toInstant(), ZoneOffset.UTC);
    }

    public ZoneId zoneOfCommunity(Long communityId) {
        if (communityId == null) return RegionTimeZones.DEFAULT;
        return communityRepository.findById(communityId)
            .map(c -> RegionTimeZones.of(c.getRegion()))
            .orElse(RegionTimeZones.DEFAULT);
    }

    /**
     * 이 방의 현재 시즌이 시작된 UTC 시각.
     *
     * <p>달력이 아니라 <b>실제 롤오버 이력</b>에서 뽑는다. 달력으로 자르면 아직 한 번도
     * 리셋된 적 없는 방(첫 시즌, 14일 규칙으로 이월된 방)의 지난달 경기가 근거 없이 사라진다.
     *
     * @return 한 번도 롤오버된 적 없으면 {@code null} — 전 기간이 곧 현재 시즌이다.
     */
    public LocalDateTime currentSeasonStartUtc(Long roomId) {
        String lastSeasonKey = snapshotRepository.findLatestSeasonKeyByRoomId(roomId);
        if (lastSeasonKey == null) return null;

        Long communityId = roomRepository.findById(roomId).map(Room::getCommunityId).orElse(null);
        return startOfSeasonUtc(zoneOfCommunity(communityId), YearMonth.parse(lastSeasonKey).plusMonths(1));
    }
}
