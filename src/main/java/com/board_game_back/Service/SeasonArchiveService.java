package com.board_game_back.Service;

import com.board_game_back.DTO.RankingDto;
import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomSeason;
import com.board_game_back.Entity.SeasonRankSnapshot;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MatchParticipantRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Repository.RoomSeasonRepository;
import com.board_game_back.Repository.SeasonRankSnapshotRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마감된 시즌 기록 조회. 순위는 {@code season_rank_snapshot}, 시즌 이름·기간은 {@code room_season}에서 나온다.
 * 기획: {@code docs/plans/plan-season-reset.md} §8, §22.
 *
 * <p><b>진행 중인 시즌은 여기 없다.</b> 스냅샷은 롤오버 때만 찍히므로 이 서비스가 아는 시즌은
 * 이미 끝난 시즌뿐이다. 현재 시즌 랭킹은 {@link RankingService}가, 진행 중 시즌 정보는
 * {@link RoomSeasonService}가 준다.
 *
 * <p><b>방은 게임 축을 하나만 쓴다.</b> 화면이 방을 {@code Room.boardGameId} 하나로 다루므로
 * ({@code GET /api/rooms/{id}/rankings}와 같은 규칙) 시즌 기록도 같은 축으로 자른다.
 * {@code boardGameId}를 주지 않으면 방의 게임으로 채운다.
 *
 * <p><b>삭제된 방·멤버·게임 행은 버린다.</b> 스냅샷에는 FK가 없어서(기록을 남기려는 의도)
 * 고아 행이 생길 수 있고, 이름을 붙일 수 없는 행은 화면에 낼 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonArchiveService {

    /** 시상 조건 — 이보다 참가자가 적은 시즌은 트로피를 주지 않는다 (§4 트로피 인플레이션 가드). */
    private static final int MIN_SEASON_PARTICIPANTS = 3;

    /** 시상 조건 — 본인이 이보다 적게 뛰었으면 트로피를 주지 않는다. */
    private static final int MIN_OWN_PLAY_COUNT = 3;

    private static final int PODIUM_SIZE = 3;

    private final SeasonRankSnapshotRepository snapshotRepository;
    private final RoomSeasonRepository seasonRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final MatchParticipantRepository participantRepository;
    private final RoomRepository roomRepository;
    private final MemberRepository memberRepository;
    private final BoardGameRepository boardGameRepository;
    private final SeasonBoundaryService boundaryService;

    /**
     * 지난 시즌 순위표 (§6 ③). 응답 스키마가 현재 랭킹과 <b>같아야</b> 프론트 테이블을
     * 그대로 재사용할 수 있다 — 그래서 스냅샷에 {@code lose_count}까지 들어 있다(§3).
     */
    public List<RankingDto.GameRankingResponse> getSeasonRanking(Long roomId, Long boardGameId, String seasonRef) {
        Long gameId = resolveBoardGameId(roomId, boardGameId);
        Long seasonId = resolveSeasonId(roomId, seasonRef);
        if (gameId == null || !belongsToRoom(seasonId, roomId)) return Collections.emptyList();

        List<SeasonRankSnapshot> snapshots =
            snapshotRepository.findByRoomSeasonIdAndBoardGameIdOrderByRankAsc(seasonId, gameId);
        Map<Long, Member> members = membersOf(snapshots);
        // 순위 분포는 스냅샷에 없어서 시즌 기간으로 자른 경기에서 센다. 첫 시즌은 시작 경계 없음(§5)
        Map<Long, List<Integer>> placements = seasonRepository.findById(seasonId)
            .map(season -> participantRepository.placementCountsByMember(roomId, gameId,
                season.getSeasonNumber() == 1 ? null : season.getStartAt(), season.getEndAt()))
            .orElse(Map.of());

        List<RankingDto.GameRankingResponse> responses = new ArrayList<>();
        for (SeasonRankSnapshot snapshot : snapshots) {
            Member member = members.get(snapshot.getMemberId());
            if (member == null) continue;
            responses.add(new RankingDto.GameRankingResponse(
                snapshot.getRank(),
                member.getId(),
                member.getNickname(),
                member.getProfileImage(),
                snapshot.getDisplayScore(),
                snapshot.getPlayCount(), snapshot.getWinCount(), snapshot.getLoseCount(),
                placements.get(member.getId())));
        }
        return responses;
    }

    /**
     * 마감된 시즌 목록, 최신순 (§6 ③의 시즌 선택). 이 게임 기록(스냅샷)이 있는 시즌만 나온다 —
     * 경기 없이 넘어간 시즌을 칩으로 띄우면 눌러도 빈 표뿐이다.
     *
     * <p>경기 수는 시즌 기간으로 자른 {@code match_record}에서 센다. 첫 시즌은 시작 경계가 없다 —
     * 시즌제 도입 전 경기는 소급 마감하지 않고 첫 시즌에 통째로 들어갔다(§5).
     */
    public List<SeasonDto.RoomSeasonResponse> getRoomSeasons(Long roomId, Long boardGameId) {
        Long gameId = resolveBoardGameId(roomId, boardGameId);
        if (gameId == null) return Collections.emptyList();

        Map<Long, Object[]> countsBySeason = new HashMap<>();
        for (Object[] row : snapshotRepository.findPlayerCountsByRoomIdAndBoardGameId(roomId, gameId)) {
            countsBySeason.put((Long) row[0], row);
        }
        if (countsBySeason.isEmpty()) return Collections.emptyList();

        ZoneId zone = boundaryService.zoneOfRoom(roomId);
        List<LocalDateTime> playedAt = matchRecordRepository.findPlayedAtByRoomIdAndBoardGameId(roomId, gameId);

        List<SeasonDto.RoomSeasonResponse> seasons = new ArrayList<>();
        for (RoomSeason season : seasonRepository.findByRoomIdAndClosedAtIsNotNullOrderBySeasonNumberDesc(roomId)) {
            Object[] row = countsBySeason.get(season.getId());
            if (row == null) continue;
            LocalDateTime from = season.getSeasonNumber() == 1 ? null : season.getStartAt();
            seasons.add(SeasonDto.RoomSeasonResponse.of(
                season, zone, legacyKey(season, (String) row[1], zone),
                countInRange(playedAt, from, season.getEndAt()), ((Number) row[2]).intValue()));
        }
        return seasons;
    }

    /**
     * 1·2·3등 시상대 (§6 ②). 시상 조건(§4)을 통과한 행만 나오므로 참가자가 3명 미만인
     * 시즌은 빈 목록이고, 본인 경기 수가 모자란 사람은 순위가 비어 보인다.
     */
    public List<SeasonDto.PodiumEntry> getPodium(Long roomId, Long boardGameId, String seasonRef) {
        Long gameId = resolveBoardGameId(roomId, boardGameId);
        Long seasonId = resolveSeasonId(roomId, seasonRef);
        if (gameId == null || !belongsToRoom(seasonId, roomId)) return Collections.emptyList();

        List<SeasonRankSnapshot> snapshots =
            snapshotRepository.findByRoomSeasonIdAndBoardGameIdOrderByRankAsc(seasonId, gameId);
        if (snapshots.size() < MIN_SEASON_PARTICIPANTS) return Collections.emptyList();

        Map<Long, Member> members = membersOf(snapshots);

        List<SeasonDto.PodiumEntry> podium = new ArrayList<>();
        for (SeasonRankSnapshot snapshot : snapshots) {
            if (snapshot.getRank() > PODIUM_SIZE) break; // 순위순이라 3위를 넘으면 뒤는 볼 필요가 없다
            if (snapshot.getPlayCount() < MIN_OWN_PLAY_COUNT) continue;

            Member member = members.get(snapshot.getMemberId());
            if (member == null) continue;
            podium.add(new SeasonDto.PodiumEntry(
                snapshot.getRank(),
                member.getId(),
                member.getNickname(),
                member.getProfileImage(),
                snapshot.getDisplayScore(),
                snapshot.getPlayCount(),
                snapshot.getWinCount()));
        }
        return podium;
    }

    /** 시즌별 내 점수 추이, 과거순 (§6 ④). 2시즌 미만이면 그래프를 숨기는 판단은 프론트가 한다. */
    public List<SeasonDto.SeasonHistoryItem> getMemberSeasonHistory(Long memberId, Long roomId, Long boardGameId) {
        Long gameId = resolveBoardGameId(roomId, boardGameId);
        if (gameId == null) return Collections.emptyList();

        List<SeasonRankSnapshot> snapshots =
            snapshotRepository.findByMemberIdAndRoomIdAndBoardGameIdOrderByRoomSeasonIdAsc(memberId, roomId, gameId);
        Map<Long, RoomSeason> seasons = seasonsOf(snapshots);
        ZoneId zone = boundaryService.zoneOfRoom(roomId);

        List<SeasonDto.SeasonHistoryItem> history = new ArrayList<>();
        for (SeasonRankSnapshot s : snapshots) {
            RoomSeason season = seasons.get(s.getRoomSeasonId());
            if (season == null) continue;
            history.add(new SeasonDto.SeasonHistoryItem(
                legacyKey(season, s.getSeasonKey(), zone),
                season.getId(), season.getSeasonNumber(), season.getName(),
                s.getDisplayScore(), s.getRank(), s.getPlayCount()));
        }
        return history;
    }

    /**
     * 프로필 트로피 선반 (§6). 1~3위 중 시상 조건(§4)을 통과한 것만, 최신 시즌부터.
     *
     * <p>조건을 여기서 거르는 이유: 2명짜리 방에서 매 시즌 금관이 나오면 금관이 아무 의미가
     * 없어진다. 스냅샷은 그대로 남기고 <b>트로피만</b> 안 준다.
     */
    public List<SeasonDto.TrophyResponse> getTrophies(Long memberId) {
        List<SeasonRankSnapshot> candidates = snapshotRepository.findTrophiesByMemberId(memberId).stream()
            .filter(s -> s.getPlayCount() >= MIN_OWN_PLAY_COUNT)
            .toList();
        if (candidates.isEmpty()) return Collections.emptyList();

        Map<Long, RoomSeason> seasons = seasonsOf(candidates);

        Map<String, Integer> participants = new HashMap<>();
        for (Object[] row : snapshotRepository.countParticipantsBySeasons(seasons.keySet())) {
            participants.put(awardKey((Long) row[0], (Long) row[1]), ((Number) row[2]).intValue());
        }

        Set<Long> roomIds = candidates.stream().map(SeasonRankSnapshot::getRoomId).collect(Collectors.toSet());
        Map<Long, String> roomNames = roomRepository.findAllById(roomIds).stream()
            .collect(Collectors.toMap(Room::getId, Room::getName));
        Set<Long> gameIds =
            candidates.stream().map(SeasonRankSnapshot::getBoardGameId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> gameNames = boardGameRepository.findByIdIn(gameIds).stream()
            .collect(Collectors.toMap(BoardGame::getId, BoardGame::getName));

        Map<Long, ZoneId> zones = new HashMap<>();
        List<SeasonDto.TrophyResponse> trophies = new ArrayList<>();
        for (SeasonRankSnapshot snapshot : candidates) {
            String key = awardKey(snapshot.getRoomSeasonId(), snapshot.getBoardGameId());
            if (participants.getOrDefault(key, 0) < MIN_SEASON_PARTICIPANTS) continue;

            RoomSeason season = seasons.get(snapshot.getRoomSeasonId());
            String roomName = roomNames.get(snapshot.getRoomId());
            String gameName = gameNames.get(snapshot.getBoardGameId());
            if (season == null || roomName == null || gameName == null) continue;

            ZoneId zone = zones.computeIfAbsent(snapshot.getRoomId(), boundaryService::zoneOfRoom);
            trophies.add(new SeasonDto.TrophyResponse(
                legacyKey(season, snapshot.getSeasonKey(), zone),
                season.getId(), season.getSeasonNumber(), season.getName(),
                snapshot.getRoomId(), roomName,
                snapshot.getBoardGameId(), gameName,
                snapshot.getRank()));
        }
        return trophies;
    }

    // ── 내부 ──

    /** 화면이 방을 게임 하나로 다루므로 {@code boardGameId}가 없으면 방의 게임으로 채운다. */
    private Long resolveBoardGameId(Long roomId, Long boardGameId) {
        if (boardGameId != null) return boardGameId;
        return roomRepository.findById(roomId).map(Room::getBoardGameId).orElse(null);
    }

    /**
     * 구버전 앱(1.10.x)이 받는 "yyyy-MM". 월간 시즌 시절 시즌은 스냅샷의 원래 키, 이후 시즌은
     * 종료일이 속한 달이다. 한 달에 시즌이 둘 끝나면 키가 겹치지만 구버전 화면은 라벨만 겹칠 뿐 죽지 않는다.
     */
    private static String legacyKey(RoomSeason season, String snapshotKey, ZoneId zone) {
        if (snapshotKey != null) return snapshotKey;
        return YearMonth.from(season.endDate(zone)).toString();
    }

    /**
     * 시즌 참조 해석. 새 앱은 시즌 id(숫자), 구버전 앱은 "yyyy-MM"을 보낸다.
     * "yyyy-MM"은 그 키({@link #legacyKey})를 가진 이 방의 가장 최근 마감 시즌으로 푼다.
     *
     * @return 해석할 수 없으면 null — 조회 결과는 빈 목록이 된다
     */
    private Long resolveSeasonId(Long roomId, String seasonRef) {
        if (seasonRef == null || seasonRef.isBlank()) return null;
        if (seasonRef.chars().allMatch(Character::isDigit)) {
            try {
                return Long.valueOf(seasonRef);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        YearMonth month;
        try {
            month = YearMonth.parse(seasonRef);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
        String key = month.toString();
        ZoneId zone = boundaryService.zoneOfRoom(roomId);
        for (RoomSeason season : seasonRepository.findByRoomIdAndClosedAtIsNotNullOrderBySeasonNumberDesc(roomId)) {
            String snapshotKey = snapshotRepository.findSeasonKeyByRoomSeasonId(season.getId());
            if (key.equals(legacyKey(season, snapshotKey, zone))) return season.getId();
        }
        return null;
    }

    /** 경로로 들어온 시즌 id라 믿을 수 없다. 다른 방의 시즌이면 조회하지 않는다. */
    private boolean belongsToRoom(Long seasonId, Long roomId) {
        if (seasonId == null) return false;
        return seasonRepository.findById(seasonId).map(s -> s.getRoomId().equals(roomId)).orElse(false);
    }

    private Map<Long, RoomSeason> seasonsOf(List<SeasonRankSnapshot> snapshots) {
        Set<Long> ids = snapshots.stream().map(SeasonRankSnapshot::getRoomSeasonId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return seasonRepository.findByIdIn(ids).stream()
            .collect(Collectors.toMap(RoomSeason::getId, Function.identity()));
    }

    private Map<Long, Member> membersOf(List<SeasonRankSnapshot> snapshots) {
        Set<Long> memberIds = snapshots.stream().map(SeasonRankSnapshot::getMemberId).collect(Collectors.toSet());
        if (memberIds.isEmpty()) return Collections.emptyMap();
        return memberRepository.findAllById(memberIds).stream()
            .collect(Collectors.toMap(Member::getId, Function.identity()));
    }

    /** {@code from}이 null이면 하한 없음 — 첫 시즌은 그 전 기간 전부를 포함한다. */
    private int countInRange(List<LocalDateTime> playedAt, LocalDateTime from, LocalDateTime to) {
        int count = 0;
        for (LocalDateTime at : playedAt) {
            if (at == null) continue;
            if (from != null && at.isBefore(from)) continue;
            if (!at.isBefore(to)) continue;
            count++;
        }
        return count;
    }

    private String awardKey(Long seasonId, Long boardGameId) {
        return seasonId + "/" + boardGameId;
    }
}
