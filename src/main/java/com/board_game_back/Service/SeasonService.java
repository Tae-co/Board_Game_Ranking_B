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
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Utils.RegionTimeZones;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커뮤니티 현황 — 커뮤니티의 전체 경기로 기록을 보여준다 (기간 제한 없음).
 * 기획: {@code docs/plans/plan-season-reset.md} §22. 처음엔 최근 30일이었으나 전체 기간으로 바꿨다.
 *
 * <p>조회할 때마다 계산하므로 경기가 등록되면 바로 반영된다.
 * playedAt은 UTC 벽시계지만 날짜는 커뮤니티 region 타임존으로 가른다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonService {

    /** 게임별 최고 점수에 오르려면 그 방·게임에서 이만큼은 뛰어야 한다. 1판 이긴 사람이 1등이 되지 않게 한다. */
    private static final int MIN_LEADER_PLAYS = 3;

    /** 게임별 최고 점수 칸 수. 칸마다 게임이 달라야 한다. */
    private static final int LEADER_SIZE = 3;

    /** 연승·연패는 2부터 상이 된다. 1연승은 그냥 1승이라 아무 정보가 아니다. */
    private static final int MIN_STREAK = 2;

    /** 승률 1위가 되려면 이만큼은 뛰어야 한다. 1판 1승 = 100%를 막는다. */
    private static final int MIN_WIN_RATE_PLAYS = 10;

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final RoomRepository roomRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final PlayerGameRatingRepository playerGameRatingRepository;
    private final MemberRepository memberRepository;

    public SeasonDto.StatusResponse getStatus(Long communityId) {
        Community community = communityRepository.findById(communityId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 커뮤니티입니다."));

        ZoneId zone = RegionTimeZones.of(community.getRegion());
        LocalDate today = LocalDate.now(zone);

        List<Long> roomIds = roomIdsOf(communityId);
        List<MatchRecord> matches = roomIds.isEmpty()
            ? Collections.emptyList()
            : matchRecordRepository.findByRoomIds(roomIds);

        // 연승·연패를 세므로 시간순이 중요하다 — 쿼리가 playedAt, id 오름차순으로 준다.
        Map<Long, PlayerTally> tallies = new LinkedHashMap<>();
        for (MatchRecord match : matches) {
            for (MatchParticipant p : match.getParticipants()) {
                Member member = p.getMember();
                if (member == null) continue;
                tallies.computeIfAbsent(member.getId(), id -> new PlayerTally(member))
                    .record(p.getPlacement(), p.getRatingChange());
            }
        }

        // from·windowDays는 구버전 앱 호환용이다 — 그 앱은 "최근 N일 · from – to"를 그리고 null이면 죽는다.
        LocalDate fromDate = matches.isEmpty()
            ? today
            : LocalDate.ofInstant(matches.get(0).getPlayedAt().toInstant(ZoneOffset.UTC), zone);
        int windowDays = (int) ChronoUnit.DAYS.between(fromDate, today) + 1;

        return new SeasonDto.StatusResponse(
            community.getId(),
            community.getName(),
            community.getImageUrl(),
            community.getInviteCode(),
            fromDate,
            today,
            windowDays,
            roomIds.size(),
            communityMemberRepository.countByCommunityId(communityId),
            matches.size(),
            buildLeaders(roomIds),
            buildAwards(tallies, matches)
        );
    }

    /**
     * 게임별 최고 점수 — 커뮤니티 방들의 (사람, 게임) 레이팅을 점수순으로 세우고, 이미 나온 게임은 건너뛰며
     * {@link #LEADER_SIZE}칸을 채운다. 같은 게임은 방이 달라도 한 게임이다. 한 사람이 여러 칸에 오를 수 있다.
     * 레이팅은 방별 시즌이 시작될 때 리셋되므로 지금 점수 = 진행 중 시즌 점수다.
     * 1판 이긴 사람이 올라오지 않게 그 방·게임에서 {@link #MIN_LEADER_PLAYS}판 이상 뛴 레이팅만 본다.
     */
    private List<SeasonDto.Leader> buildLeaders(List<Long> roomIds) {
        if (roomIds.isEmpty()) return Collections.emptyList();

        List<PlayerGameRating> sorted = playerGameRatingRepository.findByRoomIdsWithMinPlays(roomIds, MIN_LEADER_PLAYS)
            .stream()
            .sorted(Comparator.<PlayerGameRating>comparingDouble(r -> r.getGameStats().getDisplayScore()).reversed()
                .thenComparing(r -> r.getMember().getId()))
            .toList();

        List<SeasonDto.Leader> leaders = new ArrayList<>();
        Set<Long> usedGames = new HashSet<>();
        for (PlayerGameRating r : sorted) {
            if (leaders.size() >= LEADER_SIZE) break;
            if (!usedGames.add(r.getBoardGame().getId())) continue;
            Member member = r.getMember();
            leaders.add(new SeasonDto.Leader(
                leaders.size() + 1, member.getId(), member.getNickname(), member.getProfileImage(),
                Math.round(r.getGameStats().getDisplayScore()), r.getRoom().getName(), r.getBoardGame().getName()));
        }
        return leaders;
    }

    /**
     * 항목마다 독립이라 한 사람이 여러 상을 받을 수 있다. 동점이면 한 명만 고른다 (마지막 경기 승자만 예외).
     */
    private List<SeasonDto.Award> buildAwards(Map<Long, PlayerTally> tallies, List<MatchRecord> matches) {
        List<SeasonDto.Award> awards = new ArrayList<>();
        if (tallies.isEmpty()) return awards;

        // 마지막 경기 승자 — 공동 1등이면 모두
        MatchRecord last = matches.get(matches.size() - 1);
        for (MatchParticipant p : last.getParticipants()) {
            if (p.getPlacement() != 1 || p.getMember() == null) continue;
            Member m = p.getMember();
            awards.add(new SeasonDto.Award(
                "LAST_WINNER", m.getId(), m.getNickname(), m.getProfileImage(), 0, last.getBoardGame().getName()));
        }

        tallies.values().stream()
            .filter(t -> t.wins > 0)
            .max(Comparator.<PlayerTally>comparingInt(t -> t.wins).thenComparingDouble(t -> t.climb))
            .ifPresent(t -> awards.add(award("MOST_WINS", t, t.wins)));

        // 동점은 총 승수로 가른다. 5연승 1회와 5연승 + 3승은 후자가 더 잘했다.
        tallies.values().stream()
            .filter(t -> t.maxStreak >= MIN_STREAK)
            .max(Comparator.<PlayerTally>comparingInt(t -> t.maxStreak).thenComparingInt(t -> t.wins))
            .ifPresent(t -> awards.add(award("LONGEST_STREAK", t, t.maxStreak)));

        // 1등을 못 한 판이 이어진 횟수. 동점은 판수가 많은 쪽 — 더 오래 버틴 사람이다.
        tallies.values().stream()
            .filter(t -> t.maxLosingStreak >= MIN_STREAK)
            .max(Comparator.<PlayerTally>comparingInt(t -> t.maxLosingStreak).thenComparingInt(t -> t.plays))
            .ifPresent(t -> awards.add(award("LONGEST_LOSING_STREAK", t, t.maxLosingStreak)));

        // 값은 0~100(%). 동점은 판수가 많은 쪽이 더 믿을 만하다.
        tallies.values().stream()
            .filter(t -> t.plays >= MIN_WIN_RATE_PLAYS && t.wins > 0)
            .max(Comparator.<PlayerTally>comparingDouble(PlayerTally::winRate).thenComparingInt(t -> t.plays))
            .ifPresent(t -> awards.add(award("BEST_WIN_RATE", t, t.winRate() * 100)));

        tallies.values().stream()
            .max(Comparator.<PlayerTally>comparingInt(t -> t.plays).thenComparingInt(t -> t.wins))
            .ifPresent(t -> awards.add(award("MOST_PLAYED", t, t.plays)));

        // 점수판을 입력해 저장한 횟수. 기록자는 2026-10-02 이후 경기에만 있다. 동점은 먼저 기록한 사람.
        Map<Long, Integer> recordCounts = new LinkedHashMap<>();
        for (MatchRecord match : matches) {
            if (match.getRecordedByMemberId() != null) recordCounts.merge(match.getRecordedByMemberId(), 1, Integer::sum);
        }
        recordCounts.entrySet().stream()
            .max(Map.Entry.<Long, Integer>comparingByValue())
            .flatMap(top -> memberRepository.findById(top.getKey())
                .map(m -> new SeasonDto.Award(
                    "MOST_RECORDED", m.getId(), m.getNickname(), m.getProfileImage(), top.getValue(), null)))
            .ifPresent(awards::add);

        return awards;
    }

    private SeasonDto.Award award(String type, PlayerTally tally, double value) {
        return new SeasonDto.Award(
            type, tally.member.getId(), tally.member.getNickname(), tally.member.getProfileImage(), value, null);
    }

    private List<Long> roomIdsOf(Long communityId) {
        return roomRepository.findByCommunityId(communityId).stream().map(Room::getId).toList();
    }

    private static final class PlayerTally {
        final Member member;
        int plays;
        int wins;
        double climb;
        int currentStreak;
        int maxStreak;
        int currentLosingStreak;
        int maxLosingStreak;

        PlayerTally(Member member) {
            this.member = member;
        }

        /**
         * 시간순으로 한 판씩 먹인다. 1등이면 연승이 이어지고 연패가 끊긴다. 아니면 그 반대.
         *
         * <p>본인이 참가하지 않은 경기는 여기 들어오지 않으므로 연승·연패를 끊지 않는다.
         * 방·게임이 섞이는 것은 의도다 — 현황은 커뮤니티 단위이고 "우리 모임에서
         * 몇 판을 내리 이겼나(졌나)"가 세는 값이다.
         */
        void record(int placement, double ratingChange) {
            plays++;
            climb += ratingChange;
            if (placement == 1) {
                wins++;
                currentStreak++;
                maxStreak = Math.max(maxStreak, currentStreak);
                currentLosingStreak = 0;
            } else {
                currentStreak = 0;
                currentLosingStreak++;
                maxLosingStreak = Math.max(maxLosingStreak, currentLosingStreak);
            }
        }

        double winRate() {
            return plays == 0 ? 0 : (double) wins / plays;
        }
    }
}
