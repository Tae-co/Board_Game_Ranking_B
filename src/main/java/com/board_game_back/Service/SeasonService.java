package com.board_game_back.Service;

import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.MatchParticipant;
import com.board_game_back.Entity.MatchRecord;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.RoomRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonService {

    private final CommunityRepository communityRepository;
    private final RoomRepository roomRepository;
    private final MatchRecordRepository matchRecordRepository;

    public List<SeasonDto.PeriodResponse> getPeriods(Long communityId) {
        List<Long> roomIds = roomIdsOf(communityId);
        if (roomIds.isEmpty()) return Collections.emptyList();

        Map<YearMonth, Integer> countByMonth = new HashMap<>();
        for (LocalDateTime playedAt : matchRecordRepository.findPlayedAtByRoomIds(roomIds)) {
            if (playedAt == null) continue;
            countByMonth.merge(YearMonth.from(playedAt), 1, Integer::sum);
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
        LocalDateTime from = month.atDay(1).atStartOfDay();
        LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();

        List<Long> roomIds = roomIdsOf(communityId);
        List<MatchRecord> matches = roomIds.isEmpty()
            ? Collections.emptyList()
            : matchRecordRepository.findByRoomIdsAndPlayedAtRange(roomIds, from, to);

        Map<Long, PlayerTally> tallies = new LinkedHashMap<>();
        Map<Long, Map<Long, Integer>> winsByGameByMember = new LinkedHashMap<>();
        Map<Long, BoardGame> gamesById = new LinkedHashMap<>();

        for (MatchRecord match : matches) {
            BoardGame game = match.getBoardGame();
            if (game != null) gamesById.putIfAbsent(game.getId(), game);

            for (MatchParticipant p : match.getParticipants()) {
                Member member = p.getMember();
                if (member == null) continue;

                PlayerTally tally = tallies.computeIfAbsent(member.getId(), id -> new PlayerTally(member));
                tally.climb += p.getRatingChange();
                if (p.getPlacement() == 1) {
                    tally.wins++;
                    if (game != null) {
                        winsByGameByMember
                            .computeIfAbsent(game.getId(), id -> new HashMap<>())
                            .merge(member.getId(), 1, Integer::sum);
                    }
                }
            }
        }

        List<SeasonDto.Award> awards = buildAwards(tallies, roomIds, from, to);
        List<SeasonDto.GameTop> gameTops = buildGameTops(gamesById, winsByGameByMember, tallies);

        return new SeasonDto.SummaryResponse(
            community.getId(),
            community.getName(),
            community.getImageUrl(),
            community.getInviteCode(),
            month.toString(),
            matches.size(),
            tallies.size(),
            awards,
            gameTops
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

        PlayerTally biggestClimb = tallies.values().stream()
            .filter(t -> t.climb > 0 && !awarded.contains(t.member.getId()))
            .max(Comparator.comparingDouble(t -> t.climb))
            .orElse(null);
        if (biggestClimb != null) {
            awards.add(award("BIGGEST_CLIMB", biggestClimb, Math.round(biggestClimb.climb)));
            awarded.add(biggestClimb.member.getId());
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

    private List<SeasonDto.GameTop> buildGameTops(
        Map<Long, BoardGame> gamesById,
        Map<Long, Map<Long, Integer>> winsByGameByMember,
        Map<Long, PlayerTally> tallies) {

        List<SeasonDto.GameTop> tops = new ArrayList<>();
        for (BoardGame game : gamesById.values()) {
            Map<Long, Integer> wins = winsByGameByMember.get(game.getId());
            if (wins == null || wins.isEmpty()) continue;

            Map.Entry<Long, Integer> best = wins.entrySet().stream()
                .max(Map.Entry.<Long, Integer>comparingByValue()
                    .thenComparingDouble(e -> tallies.get(e.getKey()).climb))
                .orElse(null);
            if (best == null) continue;

            Member member = tallies.get(best.getKey()).member;
            tops.add(new SeasonDto.GameTop(
                game.getId(), game.getName(), game.getImageUrl(),
                member.getId(), member.getNickname(), best.getValue()
            ));
        }
        tops.sort(Comparator.comparingInt(SeasonDto.GameTop::wins).reversed());
        return tops;
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

        PlayerTally(Member member) {
            this.member = member;
        }
    }
}
