package com.board_game_back.Service;

import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Entity.MemberRole;
import com.board_game_back.Entity.RoomSeason;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomSeasonRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방의 진행 중 시즌 — 생성·조회·호스트 수정. 기획: {@code docs/plans/plan-season-reset.md} §22.
 *
 * <p><b>종료일은 내일 이후만 받는다.</b> 오늘이나 과거를 받으면 이미 지난 시각에 시즌이 끝난 것이 되어
 * 그 사이 등록된 경기가 어느 시즌 점수인지 꼬인다.
 */
@Service
@RequiredArgsConstructor
public class RoomSeasonService {

    /** 시즌 정보 없이 만들어진 방(구버전 앱)에 붙이는 기본 길이. */
    static final int DEFAULT_SEASON_DAYS = 28;

    private final RoomSeasonRepository seasonRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final SeasonRolloverService rolloverService;
    private final SeasonBoundaryService boundaryService;

    /** 방 생성과 같은 트랜잭션에서 첫 시즌을 연다. 둘 다 비었으면(구버전 앱) 기본 4주. */
    @Transactional
    public RoomSeason createFirstSeason(Long roomId, Long communityId, String name, LocalDate endDate) {
        ZoneId zone = boundaryService.zoneOfCommunity(communityId);
        LocalDate end = endDate != null ? endDate : defaultEndDate(zone);
        validateEndDate(end, zone);
        return seasonRepository.save(RoomSeason.open(roomId, 1, name, SeasonBoundaryService.nowUtc(), end, zone));
    }

    /**
     * 진행 중 시즌. 종료 시각이 지났으면 먼저 넘기고, 시즌이 아예 없는 방(구버전 앱으로 만든 방)이면
     * 오늘부터 4주짜리를 만든다 — 시즌이 없는 방을 화면마다 따로 처리하지 않으려는 것이다.
     */
    @Transactional
    public RoomSeason getCurrentSeason(Long roomId) {
        rolloverService.rolloverIfDue(roomId);
        return seasonRepository.findByRoomIdAndClosedAtIsNull(roomId).orElseGet(() -> {
            ZoneId zone = boundaryService.zoneOfRoom(roomId);
            int number = seasonRepository.findByRoomIdAndClosedAtIsNotNullOrderBySeasonNumberDesc(roomId).stream()
                .findFirst().map(s -> s.getSeasonNumber() + 1).orElse(1);
            return seasonRepository.save(
                RoomSeason.open(roomId, number, null, SeasonBoundaryService.nowUtc(), defaultEndDate(zone), zone));
        });
    }

    @Transactional
    public SeasonDto.RoomSeasonResponse getCurrentSeasonResponse(Long roomId) {
        RoomSeason season = getCurrentSeason(roomId);
        return SeasonDto.RoomSeasonResponse.of(season, boundaryService.zoneOfRoom(roomId), 0, 0);
    }

    /** 호스트가 진행 중 시즌의 이름·종료일을 바꾼다. 끝난 시즌은 기록이라 고칠 수 없다. */
    @Transactional
    public SeasonDto.RoomSeasonResponse updateCurrentSeason(
        Long roomId, Long requesterId, String name, LocalDate endDate) {

        var rm = roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId)
            .orElseThrow(() -> new IllegalArgumentException("방 멤버가 아닙니다."));
        if (rm.getRole() != MemberRole.HOST) {
            throw new IllegalStateException("방장만 수정할 수 있습니다.");
        }
        if (name == null || name.isBlank()) throw new IllegalArgumentException("시즌 이름을 입력해주세요.");
        if (endDate == null) throw new IllegalArgumentException("시즌 종료일을 입력해주세요.");

        RoomSeason season = getCurrentSeason(roomId);
        ZoneId zone = boundaryService.zoneOfRoom(roomId);
        validateEndDate(endDate, zone);

        season.rename(name);
        season.changeEndDate(endDate, zone);
        return SeasonDto.RoomSeasonResponse.of(season, zone, 0, 0);
    }

    /**
     * 재계산이 리플레이를 시작할 시각. 한 번도 마감된 적 없는 방은 {@code null} — 전 기간이 현재 시즌이다
     * (시즌제 도입 전 경기는 소급 마감하지 않으므로 첫 시즌이 도입 전 기간을 덮는다).
     */
    @Transactional(readOnly = true)
    public LocalDateTime currentSeasonStartUtc(Long roomId) {
        if (!seasonRepository.existsByRoomIdAndClosedAtIsNotNull(roomId)) return null;
        return seasonRepository.findByRoomIdAndClosedAtIsNull(roomId).map(RoomSeason::getStartAt).orElse(null);
    }

    private static LocalDate defaultEndDate(ZoneId zone) {
        return LocalDate.now(zone).plusDays(DEFAULT_SEASON_DAYS - 1);
    }

    private static void validateEndDate(LocalDate endDate, ZoneId zone) {
        if (!endDate.isAfter(LocalDate.now(zone))) {
            throw new IllegalArgumentException("시즌 종료일은 내일 이후로 정해주세요.");
        }
    }
}
