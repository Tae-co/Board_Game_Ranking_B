package com.board_game_back.Service;

import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Utils.RegionTimeZones;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시즌 날짜를 해석할 타임존과 "지금"을 정하는 한 곳.
 *
 * <p><b>타임존:</b> {@code MatchRecord.playedAt}은 UTC 벽시계인데 시즌 종료일은 커뮤니티
 * {@code region} 타임존의 날짜다. 날짜 → 시각 변환은 항상 이 zone으로 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonBoundaryService {

    private final RoomRepository roomRepository;
    private final CommunityRepository communityRepository;

    /** playedAt과 같은 축(UTC 벽시계)의 지금. */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public ZoneId zoneOfCommunity(Long communityId) {
        if (communityId == null) return RegionTimeZones.DEFAULT;
        return communityRepository.findById(communityId)
            .map(c -> RegionTimeZones.of(c.getRegion()))
            .orElse(RegionTimeZones.DEFAULT);
    }

    public ZoneId zoneOfRoom(Long roomId) {
        return zoneOfCommunity(roomRepository.findById(roomId).map(Room::getCommunityId).orElse(null));
    }
}
