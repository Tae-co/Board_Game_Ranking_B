package com.board_game_back.Service;

import com.board_game_back.DTO.RankingDto;
import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.SeasonRankSnapshot;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Repository.SeasonRankSnapshotRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
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
 * 마감된 시즌 기록 조회. 전부 {@code season_rank_snapshot} 한 테이블에서 나온다.
 * 기획: {@code docs/plans/plan-season-reset.md} §8.
 *
 * <p><b>진행 중인 시즌은 여기 없다.</b> 스냅샷은 롤오버 때만 찍히므로 이 서비스가 아는 시즌은
 * 이미 끝난 시즌뿐이다. 현재 시즌 랭킹은 {@link RankingService}가, D-n 같은 진행 표시는
 * 프론트가 계산한다(§8).
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
    private final MatchRecordRepository matchRecordRepository;
    private final RoomRepository roomRepository;
    private final MemberRepository memberRepository;
    private final BoardGameRepository boardGameRepository;
    private final SeasonBoundaryService boundaryService;

    /**
     * 지난 시즌 순위표 (§6 ③). 응답 스키마가 현재 랭킹과 <b>같아야</b> 프론트 테이블을
     * 그대로 재사용할 수 있다 — 그래서 스냅샷에 {@code lose_count}까지 들어 있다(§3).
     */
    public List<RankingDto.GameRankingResponse> getSeasonRanking(
        Long roomId, Long boardGameId, String seasonKey) {

        Long gameId = resolveBoardGameId(roomId, boardGameId);
        if (gameId == null || !isSeasonKey(seasonKey)) return Collections.emptyList();

        List<SeasonRankSnapshot> snapshots =
            snapshotRepository.findByRoomIdAndBoardGameIdAndSeasonKeyOrderByRankAsc(roomId, gameId, seasonKey);
        Map<Long, Member> members = membersOf(snapshots);

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
                snapshot.getPlayCount(), snapshot.getWinCount(), snapshot.getLoseCount()));
        }
        return responses;
    }

    /**
     * 마감된 시즌 목록, 최신순 (§6 ③의 월 선택).
     *
     * <p>경기 수는 시즌 경계로 자른 {@code match_record}에서 센다. 경계는 달력이 아니라
     * <b>스냅샷 이력</b>에서 나온다 — 14일 규칙으로 이월된 방은 첫 시즌이 두 달치이므로
     * 달력으로 자르면 그 방의 첫 시즌 경기 수가 실제보다 적게 나온다.
     */
    public List<SeasonDto.RoomSeasonResponse> getRoomSeasons(Long roomId, Long boardGameId) {
        Long gameId = resolveBoardGameId(roomId, boardGameId);
        if (gameId == null) return Collections.emptyList();

        List<Object[]> rows = snapshotRepository.findPlayerCountsByRoomIdAndBoardGameId(roomId, gameId);
        if (rows.isEmpty()) return Collections.emptyList();

        ZoneId zone = boundaryService.zoneOfCommunity(communityIdOf(roomId));
        List<LocalDateTime> playedAt = matchRecordRepository.findPlayedAtByRoomIdAndBoardGameId(roomId, gameId);

        // 쿼리는 최신순이다. 경기 수는 이전 시즌의 끝에서부터 세야 하므로 과거순으로 훑는다.
        List<Object[]> ascending = new ArrayList<>(rows);
        Collections.reverse(ascending);

        List<SeasonDto.RoomSeasonResponse> seasons = new ArrayList<>();
        LocalDateTime seasonStart = null; // 첫 시즌은 시작 경계가 없다 — 그 전 기간 전부가 첫 시즌이다
        for (Object[] row : ascending) {
            String seasonKey = (String) row[0];
            int playerCount = ((Number) row[1]).intValue();
            LocalDateTime seasonEnd =
                SeasonBoundaryService.startOfSeasonUtc(zone, YearMonth.parse(seasonKey).plusMonths(1));

            seasons.add(new SeasonDto.RoomSeasonResponse(
                seasonKey, countInRange(playedAt, seasonStart, seasonEnd), playerCount));
            seasonStart = seasonEnd;
        }

        Collections.reverse(seasons);
        return seasons;
    }

    /**
     * 1·2·3등 시상대 (§6 ②). 시상 조건(§4)을 통과한 행만 나오므로 참가자가 3명 미만인
     * 시즌은 빈 목록이고, 본인 경기 수가 모자란 사람은 순위가 비어 보인다.
     */
    public List<SeasonDto.PodiumEntry> getPodium(Long roomId, Long boardGameId, String seasonKey) {
        Long gameId = resolveBoardGameId(roomId, boardGameId);
        if (gameId == null || !isSeasonKey(seasonKey)) return Collections.emptyList();

        List<SeasonRankSnapshot> snapshots =
            snapshotRepository.findByRoomIdAndBoardGameIdAndSeasonKeyOrderByRankAsc(roomId, gameId, seasonKey);
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
    public List<SeasonDto.SeasonHistoryItem> getMemberSeasonHistory(
        Long memberId, Long roomId, Long boardGameId) {

        Long gameId = resolveBoardGameId(roomId, boardGameId);
        if (gameId == null) return Collections.emptyList();

        return snapshotRepository
            .findByMemberIdAndRoomIdAndBoardGameIdOrderBySeasonKeyAsc(memberId, roomId, gameId)
            .stream()
            .map(s -> new SeasonDto.SeasonHistoryItem(
                s.getSeasonKey(), s.getDisplayScore(), s.getRank(), s.getPlayCount()))
            .toList();
    }

    /**
     * 프로필 트로피 선반 (§6). 1~3위 중 시상 조건(§4)을 통과한 것만, 최신 시즌부터.
     *
     * <p>조건을 여기서 거르는 이유: 2명짜리 방에서 매달 금관이 나오면 금관이 아무 의미가
     * 없어진다. 스냅샷은 그대로 남기고 <b>트로피만</b> 안 준다.
     */
    public List<SeasonDto.TrophyResponse> getTrophies(Long memberId) {
        List<SeasonRankSnapshot> candidates = snapshotRepository.findTrophiesByMemberId(memberId).stream()
            .filter(s -> s.getPlayCount() >= MIN_OWN_PLAY_COUNT)
            .toList();
        if (candidates.isEmpty()) return Collections.emptyList();

        Set<Long> roomIds = candidates.stream().map(SeasonRankSnapshot::getRoomId).collect(Collectors.toSet());
        Set<String> seasonKeys =
            candidates.stream().map(SeasonRankSnapshot::getSeasonKey).collect(Collectors.toSet());

        Map<String, Integer> participants = new HashMap<>();
        for (Object[] row : snapshotRepository.countParticipantsByRoomsAndSeasons(roomIds, seasonKeys)) {
            participants.put(
                awardKey((String) row[0], (Long) row[1], (Long) row[2]), ((Number) row[3]).intValue());
        }

        Map<Long, String> roomNames = roomRepository.findAllById(roomIds).stream()
            .collect(Collectors.toMap(Room::getId, Room::getName));
        Set<Long> gameIds =
            candidates.stream().map(SeasonRankSnapshot::getBoardGameId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> gameNames = boardGameRepository.findByIdIn(gameIds).stream()
            .collect(Collectors.toMap(BoardGame::getId, BoardGame::getName));

        List<SeasonDto.TrophyResponse> trophies = new ArrayList<>();
        for (SeasonRankSnapshot snapshot : candidates) {
            String key = awardKey(snapshot.getSeasonKey(), snapshot.getRoomId(), snapshot.getBoardGameId());
            if (participants.getOrDefault(key, 0) < MIN_SEASON_PARTICIPANTS) continue;

            String roomName = roomNames.get(snapshot.getRoomId());
            String gameName = gameNames.get(snapshot.getBoardGameId());
            if (roomName == null || gameName == null) continue;

            trophies.add(new SeasonDto.TrophyResponse(
                snapshot.getSeasonKey(),
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

    private Long communityIdOf(Long roomId) {
        return roomRepository.findById(roomId).map(Room::getCommunityId).orElse(null);
    }

    private Map<Long, Member> membersOf(List<SeasonRankSnapshot> snapshots) {
        Set<Long> memberIds =
            snapshots.stream().map(SeasonRankSnapshot::getMemberId).collect(Collectors.toSet());
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

    private String awardKey(String seasonKey, Long roomId, Long boardGameId) {
        return seasonKey + "/" + roomId + "/" + boardGameId;
    }

    /** 경로·쿼리로 들어온 값이라 형식을 믿을 수 없다. 'yyyy-MM'이 아니면 조회 자체를 하지 않는다. */
    private boolean isSeasonKey(String seasonKey) {
        if (seasonKey == null) return false;
        try {
            YearMonth.parse(seasonKey);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
