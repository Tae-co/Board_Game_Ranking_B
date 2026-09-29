package com.board_game_back.Service;

import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.MatchParticipant;
import com.board_game_back.Entity.MatchRecord;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Utils.RegionTimeZones;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커뮤니티 월간 결산. 시즌 엔티티 없이 MatchRecord.playedAt의 연-월로 집계한다.
 *
 * <p>playedAt은 UTC 벽시계지만 달은 커뮤니티 region 타임존으로 가른다 — 시즌 경계
 * ({@link SeasonBoundaryService})와 같은 기준이어야 한국 1일 새벽 경기가 지난달 결산에 섞이지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonService {

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final RoomRepository roomRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final SeasonArchiveService archiveService;
    private final SeasonBoundaryService boundaryService;

    /** 연승은 2부터 상이 된다. 1연승은 그냥 1승이라 아무 정보가 아니다. */
    private static final int MIN_STREAK = 2;

    public List<SeasonDto.PeriodResponse> getPeriods(Long communityId) {
        List<Long> roomIds = roomIdsOf(communityId);
        if (roomIds.isEmpty()) return Collections.emptyList();

        ZoneId zone = boundaryService.zoneOfCommunity(communityId);
        Map<YearMonth, Integer> countByMonth = new HashMap<>();
        for (LocalDateTime playedAt : matchRecordRepository.findPlayedAtByRoomIds(roomIds)) {
            if (playedAt == null) continue;
            countByMonth.merge(YearMonth.from(playedAt.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone)), 1, Integer::sum);
        }

        return countByMonth.entrySet().stream()
            .sorted(Map.Entry.<YearMonth, Integer>comparingByKey().reversed())
            .map(e -> new SeasonDto.PeriodResponse(e.getKey().toString(), e.getValue()))
            .toList();
    }

    public SeasonDto.SummaryResponse getSummary(Long communityId, String period) {
        Community community = communityRepository.findById(communityId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 커뮤니티입니다."));

        YearMonth month = parsePeriod(period);
        ZoneId zone = RegionTimeZones.of(community.getRegion());
        LocalDateTime from = SeasonBoundaryService.startOfSeasonUtc(zone, month);
        LocalDateTime to = SeasonBoundaryService.startOfSeasonUtc(zone, month.plusMonths(1));

        List<Long> roomIds = roomIdsOf(communityId);
        List<MatchRecord> matches = roomIds.isEmpty()
            ? Collections.emptyList()
            : matchRecordRepository.findByRoomIdsAndPlayedAtRange(roomIds, from, to);

        // 연승을 세므로 시간순이 중요하다 — 쿼리가 playedAt, id 오름차순으로 준다.
        Map<Long, PlayerTally> tallies = new LinkedHashMap<>();
        for (MatchRecord match : matches) {
            for (MatchParticipant p : match.getParticipants()) {
                Member member = p.getMember();
                if (member == null) continue;
                tallies.computeIfAbsent(member.getId(), id -> new PlayerTally(member))
                    .record(p.getPlacement(), p.getRatingChange());
            }
        }

        return new SeasonDto.SummaryResponse(
            community.getId(),
            community.getName(),
            community.getImageUrl(),
            community.getInviteCode(),
            month.toString(),
            roomIds.size(),
            communityMemberRepository.countByCommunityId(communityId),
            buildAwards(tallies, roomIds, from, to),
            archiveService.getCommunityPodium(roomIds, month.toString())
        );
    }

    private List<SeasonDto.Award> buildAwards(
        Map<Long, PlayerTally> tallies, List<Long> roomIds, LocalDateTime from, LocalDateTime to) {

        List<SeasonDto.Award> awards = new ArrayList<>();
        if (tallies.isEmpty()) return awards;

        Set<Long> awarded = new HashSet<>();

        PlayerTally mostWins = tallies.values().stream()
            .filter(t -> t.wins > 0)
            .max(Comparator.<PlayerTally>comparingInt(t -> t.wins).thenComparingDouble(t -> t.climb))
            .orElse(null);
        if (mostWins != null) {
            awards.add(award("MOST_WINS", mostWins, mostWins.wins));
            awarded.add(mostWins.member.getId());
        }

        // 동점은 총 승수로 가른다. 5연승 1회와 5연승 + 3승은 후자가 더 한 시즌을 이끌었다.
        PlayerTally longestStreak = tallies.values().stream()
            .filter(t -> t.maxStreak >= MIN_STREAK && !awarded.contains(t.member.getId()))
            .max(Comparator.<PlayerTally>comparingInt(t -> t.maxStreak).thenComparingInt(t -> t.wins))
            .orElse(null);
        if (longestStreak != null) {
            awards.add(award("LONGEST_STREAK", longestStreak, longestStreak.maxStreak));
            awarded.add(longestStreak.member.getId());
        }

        // 다크호스: 커뮤니티에서 이번 시즌에 처음 뛴 멤버 중 상승폭 1위
        Set<Long> newcomers = newcomerIds(roomIds, from, to);
        PlayerTally darkHorse = tallies.values().stream()
            .filter(t -> newcomers.contains(t.member.getId()) && !awarded.contains(t.member.getId()))
            .max(Comparator.comparingDouble(t -> t.climb))
            .orElse(null);
        if (darkHorse != null) {
            awards.add(award("DARK_HORSE", darkHorse, Math.round(darkHorse.climb)));
        }

        return awards;
    }

    private Set<Long> newcomerIds(List<Long> roomIds, LocalDateTime from, LocalDateTime to) {
        if (roomIds.isEmpty()) return Collections.emptySet();
        Set<Long> newcomers = new HashSet<>();
        for (Object[] row : matchRecordRepository.findFirstPlayedAtByMember(roomIds)) {
            Long memberId = (Long) row[0];
            LocalDateTime firstPlayedAt = (LocalDateTime) row[1];
            if (firstPlayedAt != null && !firstPlayedAt.isBefore(from) && firstPlayedAt.isBefore(to)) {
                newcomers.add(memberId);
            }
        }
        return newcomers;
    }

    private SeasonDto.Award award(String type, PlayerTally tally, double value) {
        return new SeasonDto.Award(
            type, tally.member.getId(), tally.member.getNickname(), tally.member.getProfileImage(), value);
    }

    private List<Long> roomIdsOf(Long communityId) {
        return roomRepository.findByCommunityId(communityId).stream().map(Room::getId).toList();
    }

    private YearMonth parsePeriod(String period) {
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("시즌 형식이 잘못되었습니다. (예: 2026-08)");
        }
    }

    private static final class PlayerTally {
        final Member member;
        int wins;
        double climb;
        int currentStreak;
        int maxStreak;

        PlayerTally(Member member) {
            this.member = member;
        }

        /**
         * 시간순으로 한 판씩 먹인다. 1등이면 연승이 이어지고 아니면 끊긴다.
         *
         * <p>본인이 참가하지 않은 경기는 여기 들어오지 않으므로 연승을 끊지 않는다.
         * 방·게임이 섞이는 것은 의도다 — 결산은 커뮤니티 단위이고 "그 달 우리 모임에서
         * 몇 판을 내리 이겼나"가 세는 값이다.
         */
        void record(int placement, double ratingChange) {
            climb += ratingChange;
            if (placement == 1) {
                wins++;
                currentStreak++;
                maxStreak = Math.max(maxStreak, currentStreak);
            } else {
                currentStreak = 0;
            }
        }
    }
}
