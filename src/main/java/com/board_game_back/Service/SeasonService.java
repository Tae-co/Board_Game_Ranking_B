package com.board_game_back.Service;

import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.MatchParticipant;
import com.board_game_back.Entity.MatchRecord;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Utils.RegionTimeZones;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
 * 커뮤니티 현황 — 최근 {@link #WINDOW_DAYS}일 경기로 "지금 누가 잘하고 있나"를 보여준다.
 * 기획: {@code docs/plans/plan-season-reset.md} §22.
 *
 * <p>시즌은 방마다 따로 돌아서(§22) 커뮤니티를 한 시즌으로 묶을 수 없다. 그래서 커뮤니티는
 * 날짜가 계속 밀려가는 기간으로 본다. 조회할 때마다 계산하므로 경기가 등록되면 바로 반영된다.
 *
 * <p>playedAt은 UTC 벽시계지만 날짜는 커뮤니티 region 타임존으로 가른다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonService {

    static final int WINDOW_DAYS = 30;

    /** 시상대에 오르려면 그 방·게임에서 이만큼은 뛰어야 한다. 1판 이긴 사람이 1등이 되지 않게 한다. */
    private static final int MIN_LEADER_PLAYS = 3;

    private static final int LEADER_SIZE = 3;

    /** 연승은 2부터 상이 된다. 1연승은 그냥 1승이라 아무 정보가 아니다. */
    private static final int MIN_STREAK = 2;

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final RoomRepository roomRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final PlayerGameRatingRepository playerGameRatingRepository;

    public SeasonDto.StatusResponse getStatus(Long communityId) {
        Community community = communityRepository.findById(communityId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 커뮤니티입니다."));

        ZoneId zone = RegionTimeZones.of(community.getRegion());
        LocalDate today = LocalDate.now(zone);
        LocalDate fromDate = today.minusDays(WINDOW_DAYS - 1);
        LocalDateTime from = toUtc(fromDate, zone);
        LocalDateTime to = toUtc(today.plusDays(1), zone);

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

        return new SeasonDto.StatusResponse(
            community.getId(),
            community.getName(),
            community.getImageUrl(),
            community.getInviteCode(),
            fromDate,
            today,
            WINDOW_DAYS,
            roomIds.size(),
            communityMemberRepository.countByCommunityId(communityId),
            matches.size(),
            buildLeaders(roomIds),
            buildAwards(tallies, roomIds, from, to)
        );
    }

    /**
     * 시상대 — 커뮤니티 방들에서 가장 높은 점수를 가진 3명. 사람마다 최고 점수인 방 하나만 본다.
     * 레이팅은 방별 시즌이 시작될 때 리셋되므로 지금 점수 = 진행 중 시즌 점수다.
     * 1판 이긴 사람이 올라오지 않게 그 방·게임에서 {@link #MIN_LEADER_PLAYS}판 이상 뛴 레이팅만 본다.
     */
    private List<SeasonDto.Leader> buildLeaders(List<Long> roomIds) {
        if (roomIds.isEmpty()) return Collections.emptyList();

        Map<Long, PlayerGameRating> bestByMember = new HashMap<>();
        for (PlayerGameRating rating : playerGameRatingRepository.findByRoomIdsWithMinPlays(roomIds, MIN_LEADER_PLAYS)) {
            bestByMember.merge(rating.getMember().getId(), rating,
                (a, b) -> b.getGameStats().getDisplayScore() > a.getGameStats().getDisplayScore() ? b : a);
        }
        List<PlayerGameRating> sorted = bestByMember.values().stream()
            .sorted(Comparator.<PlayerGameRating>comparingDouble(r -> r.getGameStats().getDisplayScore()).reversed()
                .thenComparing(r -> r.getMember().getId()))
            .toList();

        List<SeasonDto.Leader> leaders = new ArrayList<>();
        int rank = 0;
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < sorted.size(); i++) {
            PlayerGameRating r = sorted.get(i);
            long score = Math.round(r.getGameStats().getDisplayScore());
            if (score != previous) {
                rank = i + 1;
                previous = score;
            }
            if (rank > LEADER_SIZE) break;
            Member member = r.getMember();
            leaders.add(new SeasonDto.Leader(
                rank, member.getId(), member.getNickname(), member.getProfileImage(),
                score, r.getRoom().getName()));
        }
        return leaders;
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

        // 다크호스: 기간 안에 커뮤니티 첫 경기를 한 멤버 중 상승폭 1위
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

    private static LocalDateTime toUtc(LocalDate date, ZoneId zone) {
        return LocalDateTime.ofInstant(date.atStartOfDay(zone).toInstant(), ZoneOffset.UTC);
    }

    private static final class PlayerTally {
        final Member member;
        int plays;
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
         * 방·게임이 섞이는 것은 의도다 — 결산은 커뮤니티 단위이고 "기간 안에 우리 모임에서
         * 몇 판을 내리 이겼나"가 세는 값이다.
         */
        void record(int placement, double ratingChange) {
            plays++;
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
