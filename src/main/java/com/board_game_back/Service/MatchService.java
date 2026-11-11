package com.board_game_back.Service;

import com.board_game_back.Exception.AppException;
import com.board_game_back.Exception.ErrorCode;
import com.board_game_back.DTO.MatchDto;
import com.board_game_back.DTO.MatchDto.ResultResponse;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.MatchParticipant;
import com.board_game_back.Entity.MatchRecord;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.MemberRole;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomMember;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Utils.RatingConstants;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRecordRepository matchRecordRepository;
    private final PlayerGameRatingRepository ratingRepository;
    private final BoardGameRepository boardGameRepository;
    private final MemberRepository memberRepository;
    private final RatingCalculator ratingCalculator;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomSeasonService roomSeasonService;
    private final SeasonRolloverService seasonRolloverService;

    @Transactional
    public List<ResultResponse> recordMatchResult(MatchDto.ResultRequest request, Long requesterId) {
        roomMemberRepository.findByRoomIdAndMemberId(request.roomId(), requesterId)
            .orElseThrow(() -> new AppException(ErrorCode.ROOM_MEMBER_ONLY_RECORD));

        BoardGame game = boardGameRepository.findById(request.boardGameId())
            .orElseThrow(() -> new AppException(ErrorCode.UNKNOWN_GAME));

        Room room = roomRepository.findById(request.roomId())
            .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        // 점수는 (멤버, 게임, 방)마다 한 줄이다. 방의 게임이 아닌 경기를 받으면 같은 방에 멤버 점수 줄이
        // 하나 더 생겨, 방 랭킹에는 안 보이는 점수가 쌓이고 LP 수정 같은 (멤버, 방) 조회가 깨진다.
        // 게임이 비어 있는 방은 한 방 한 게임(2026-03) 이전에 만든 방이라 막지 않는다.
        if (room.getBoardGameId() != null && !room.getBoardGameId().equals(game.getId())) {
            throw new AppException(ErrorCode.MATCH_GAME_NOT_ROOM_GAME);
        }

        // 정각 스케줄러를 기다리면 시즌 종료 후 최대 1시간 동안의 경기가 지난 시즌 점수에 섞인다 (§22).
        seasonRolloverService.rolloverIfDue(room.getId());

        MatchRecord matchRecord = MatchRecord.builder().boardGame(game).room(room).recordedByMemberId(requesterId).build();

        List<RatingCalculator.PlayerResult> calcResults = new ArrayList<>();
        List<MatchParticipant> participants = new ArrayList<>();
        List<PlayerGameRating> gameRatings = new ArrayList<>();

        for (MatchDto.ParticipantRequest pr : request.participants()) {
            Member member = memberRepository.findById(pr.memberId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            // 참가자는 반드시 이 방의 멤버여야 한다. 요청자만 검증하면 임의 memberId를
            // 참가자로 넣어 남의 레이팅을 조작할 수 있으므로 각 참가자도 검증한다. (#17)
            roomMemberRepository.findByRoomIdAndMemberId(request.roomId(), pr.memberId())
                .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_ROOM_MEMBER));

            PlayerGameRating gameRating = ratingRepository.findByMemberAndBoardGameAndRoom(member, game, room)
                .orElseGet(() -> ratingRepository.save(
                    PlayerGameRating.builder().member(member).boardGame(game).room(room).build()
                ));

            calcResults.add(new RatingCalculator.PlayerResult(
                member.getId(), pr.placement(), gameRating.getPlayCount(), gameRating.getGameStats()
            ));

            MatchParticipant mp = new MatchParticipant(matchRecord, member, pr.placement());
            if (pr.scoresJson() != null) mp.updateScoresJson(pr.scoresJson());
            applyChipRequest(mp, game, pr);
            participants.add(mp);
            gameRatings.add(gameRating);
        }

        if (game.isChipGame()) {
            List<MatchDto.ResultResponse> responseList = new ArrayList<>();
            for (int i = 0; i < participants.size(); i++) {
                MatchParticipant participant = participants.get(i);
                PlayerGameRating gameRating = gameRatings.get(i);
                applyChipResult(participant, gameRating, matchRecord.getPlayedAt());
                ratingRepository.save(gameRating);
                responseList.add(new MatchDto.ResultResponse(
                    participant.getMember().getId(), participant.getMember().getNickname(),
                    participant.getPlacement(), participant.getRatingChange()
                ));
            }
            matchRecordRepository.save(matchRecord);
            return responseList;
        }

        ratingCalculator.calculateMultiplayerRatings(
            calcResults, RatingConstants.isCasualRules(matchRecord.getPlayedAt()));

        List<MatchDto.ResultResponse> responseList = new ArrayList<>();

        for (int i = 0; i < participants.size(); i++) {
            MatchParticipant participant = participants.get(i);
            RatingCalculator.PlayerResult calcResult = calcResults.get(i);
            PlayerGameRating gameRating = gameRatings.get(i);
            Member member = participant.getMember();

            double ratingChange = calcResult.newStats.getDisplayScore()
                - gameRating.getGameStats().getDisplayScore();
            participant.updateRatingChange(ratingChange);

            gameRating.getGameStats().update(
                calcResult.newStats.getRating(),
                calcResult.newStats.getRatingDeviation(),
                0.0
            );
            gameRating.addPlayCount();
            gameRating.updateLastPlayedAt(matchRecord.getPlayedAt());
            if (participant.getPlacement() == 1) {
                gameRating.addWinCount();
            } else {
                gameRating.addLoseCount();
            }

            double newMu = calcResult.newStats.getRating();
            double newDisplayScore = calcResult.newStats.getDisplayScore();
            boolean memberChanged = false;
            if (newMu > member.getOverallStats().getRating()) {
                member.getOverallStats().update(newMu, calcResult.newStats.getRatingDeviation(), 0.0);
                memberChanged = true;
            }
            if (newDisplayScore > member.getBestDisplayScore()) {
                member.updateBestDisplayScore(newDisplayScore);
                memberChanged = true;
            }
            if (memberChanged) memberRepository.save(member);

            ratingRepository.save(gameRating);
            responseList.add(new MatchDto.ResultResponse(
                member.getId(), member.getNickname(), participant.getPlacement(), ratingChange
            ));
        }

        matchRecordRepository.save(matchRecord);
        return responseList;
    }

    @Transactional
    public List<MatchDto.MatchHistoryResponse> getMatchHistory(Long roomId) {
        Room room = roomRepository.findById(roomId).orElseThrow();
        List<MatchRecord> matches = room.getBoardGameId() != null
            ? matchRecordRepository.findByRoomIdAndBoardGameIdWithParticipants(roomId, room.getBoardGameId())
            : matchRecordRepository.findByRoomIdWithParticipants(roomId);

        return matches.stream()
            .map(m -> new MatchDto.MatchHistoryResponse(
                m.getId(),
                m.getBoardGame().getId(),
                m.getBoardGame().getName(),
                m.getPlayedAt().toString() + "Z",
                m.getParticipants().stream()
                    .map(p -> new MatchDto.ParticipantHistoryResponse(
                        p.getMember().getId(),
                        p.getMember().getNickname(),
                        p.getMember().getProfileImage(),
                        p.getPlacement(),
                        p.getRatingChange(),
                        p.getScoresJson()
                    ))
                    .sorted(Comparator.comparingInt(MatchDto.ParticipantHistoryResponse::placement))
                    .collect(Collectors.toList())
            ))
            .collect(Collectors.toList());
    }

    @Transactional
    public List<MatchDto.ResultResponse> updateMatchResult(Long matchId, MatchDto.ResultRequest request, Long requesterId) {
        MatchRecord match = matchRecordRepository.findById(matchId)
            .orElseThrow(() -> new AppException(ErrorCode.MATCH_NOT_FOUND));

        Long roomId = match.getRoom().getId();
        RoomMember rm = roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_ROOM_MEMBER));
        if (rm.getRole() != MemberRole.HOST) {
            throw new AppException(ErrorCode.ROOM_OWNER_ONLY_EDIT_MATCH);
        }

        Long boardGameId = match.getBoardGame().getId();

        List<MatchParticipant> newParticipants = new ArrayList<>();
        for (MatchDto.ParticipantRequest pr : request.participants()) {
            Member member = memberRepository.findById(pr.memberId()).orElseThrow();
            MatchParticipant newMp = new MatchParticipant(match, member, pr.placement());
            if (pr.scoresJson() != null) newMp.updateScoresJson(pr.scoresJson());
            applyChipRequest(newMp, match.getBoardGame(), pr);
            newParticipants.add(newMp);
        }

        match.getParticipants().clear();
        match.getParticipants().addAll(newParticipants);
        matchRecordRepository.save(match);

        recalculateRatings(roomId, boardGameId);

        List<MatchDto.ResultResponse> responseList = new ArrayList<>();
        for (MatchParticipant mp : match.getParticipants()) {
            Member member = mp.getMember();
            responseList.add(new MatchDto.ResultResponse(
                member.getId(), member.getNickname(), mp.getPlacement(), mp.getRatingChange()
            ));
        }
        return responseList;
    }

    @Transactional
    public void deleteMatch(Long matchId, Long requesterId) {
        MatchRecord match = matchRecordRepository.findById(matchId)
            .orElseThrow(() -> new AppException(ErrorCode.MATCH_TO_DELETE_NOT_FOUND));

        if (match.getRoom() == null) {
            throw new AppException(ErrorCode.MATCH_WITHOUT_ROOM);
        }

        Long roomId = match.getRoom().getId();
        Long boardGameId = match.getBoardGame().getId();

        RoomMember roomMember = roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_ROOM_MEMBER));
        if (roomMember.getRole() != MemberRole.HOST) {
            throw new AppException(ErrorCode.ROOM_OWNER_ONLY_DELETE_MATCH);
        }

        matchRecordRepository.delete(match);
        recalculateRatings(roomId, boardGameId);
    }

    private void recalculateRatings(Long roomId, Long boardGameId) {
        Room room = roomRepository.findById(roomId).orElseThrow();
        BoardGame game = boardGameRepository.findById(boardGameId).orElseThrow();

        List<PlayerGameRating> allRatings = ratingRepository
            .findByRoomIdAndBoardGameIdOrderByGameStatsRatingDesc(roomId, boardGameId);
        Map<Long, PlayerGameRating> ratingByMemberId = new HashMap<>();
        for (PlayerGameRating gr : allRatings) {
            gr.reset();
            ratingByMemberId.put(gr.getMember().getId(), gr);
        }

        List<MatchRecord> matches = matchRecordRepository
            .findByRoomIdAndBoardGameIdWithParticipantsAsc(roomId, boardGameId);

        // 리셋된 적 있는 방은 현재 시즌 경기만 리플레이한다. 이게 없으면 재계산 한 번에
        // 지난 시즌 점수가 통째로 부활해 롤오버가 무의미해진다. null = 아직 리셋된 적 없음.
        LocalDateTime seasonStart = roomSeasonService.currentSeasonStartUtc(roomId);

        for (MatchRecord match : matches) {
            if (seasonStart != null && match.getPlayedAt() != null
                && match.getPlayedAt().isBefore(seasonStart)) continue;

            if (game.isChipGame()) {
                for (MatchParticipant mp : match.getParticipants()) {
                    PlayerGameRating gr = ratingByMemberId.computeIfAbsent(mp.getMember().getId(), id ->
                        ratingRepository.save(PlayerGameRating.builder()
                            .member(mp.getMember()).boardGame(game).room(room).build()));
                    applyChipResult(mp, gr, match.getPlayedAt());
                }
                continue;
            }

            List<RatingCalculator.PlayerResult> calcResults = new ArrayList<>();
            List<MatchParticipant> participants = match.getParticipants();

            for (MatchParticipant mp : participants) {
                Long memberId = mp.getMember().getId();
                PlayerGameRating gr = ratingByMemberId.computeIfAbsent(memberId, id -> {
                    PlayerGameRating nr = PlayerGameRating.builder()
                        .member(mp.getMember()).boardGame(game).room(room).build();
                    return ratingRepository.save(nr);
                });
                calcResults.add(new RatingCalculator.PlayerResult(
                    memberId, mp.getPlacement(), gr.getPlayCount(), gr.getGameStats()
                ));
            }

            if (calcResults.size() < 2) continue;

            ratingCalculator.calculateMultiplayerRatings(
                calcResults, RatingConstants.isCasualRules(match.getPlayedAt()));

            for (int i = 0; i < participants.size(); i++) {
                MatchParticipant mp = participants.get(i);
                RatingCalculator.PlayerResult result = calcResults.get(i);
                Long memberId = mp.getMember().getId();
                PlayerGameRating gr = ratingByMemberId.get(memberId);

                double ratingChange = result.newStats.getDisplayScore() - gr.getGameStats().getDisplayScore();
                mp.updateRatingChange(ratingChange);

                gr.getGameStats().update(result.newStats.getRating(), result.newStats.getRatingDeviation(), 0.0);
                gr.addPlayCount();
                gr.updateLastPlayedAt(match.getPlayedAt());
                if (mp.getPlacement() == 1) gr.addWinCount();
                else gr.addLoseCount();
            }
        }

        ratingRepository.saveAll(ratingByMemberId.values());

        Set<Long> currentMemberIds = roomMemberRepository.findByRoomId(roomId)
            .stream().map(rm -> rm.getMember().getId()).collect(Collectors.toSet());
        List<PlayerGameRating> leftMemberRatings = ratingByMemberId.values().stream()
            .filter(gr -> !currentMemberIds.contains(gr.getMember().getId()))
            .collect(Collectors.toList());
        if (!leftMemberRatings.isEmpty()) ratingRepository.deleteAll(leftMemberRatings);

        for (PlayerGameRating gr : ratingByMemberId.values()) {
            Member member = gr.getMember();
            // 칩 게임 잔고는 Elo가 아니라 전체 랭킹에 섞지 않는다.
            List<PlayerGameRating> allPlayed = ratingRepository.findPlayedEloByMemberId(member.getId());
            allPlayed.stream()
                .max(Comparator.comparingDouble(r -> r.getGameStats().getDisplayScore()))
                .ifPresentOrElse(
                    best -> {
                        member.getOverallStats().update(
                            best.getGameStats().getRating(),
                            best.getGameStats().getRatingDeviation(),
                            0.0);
                        member.setBestDisplayScore(best.getGameStats().getDisplayScore());
                    },
                    member::resetBestDisplayScore
                );
            memberRepository.save(member);
        }
    }

    /** 칩 게임은 손익과 바이인 횟수가 있어야 기록할 수 있다. Elo 게임은 무시한다. */
    private void applyChipRequest(MatchParticipant mp, BoardGame game, MatchDto.ParticipantRequest pr) {
        if (!game.isChipGame()) return;
        if (pr.chipDelta() == null) throw new AppException(ErrorCode.CHIP_DELTA_REQUIRED);
        if (pr.buyIns() == null || pr.buyIns() < 1) throw new AppException(ErrorCode.BUY_INS_INVALID);
        mp.updateChipResult(pr.chipDelta(), pr.buyIns());
    }

    /** 칩 게임 한 판: Elo·전체 랭킹을 건드리지 않고 손익만 누적한다. 1등(우승자)만 승리로 센다. */
    private void applyChipResult(MatchParticipant mp, PlayerGameRating gr, LocalDateTime playedAt) {
        int delta = mp.getChipDelta() != null ? mp.getChipDelta() : 0;
        int rebuys = mp.getBuyIns() != null ? Math.max(0, mp.getBuyIns() - 1) : 0;
        gr.applyChipResult(delta, rebuys);
        mp.updateRatingChange(delta);
        gr.addPlayCount();
        gr.updateLastPlayedAt(playedAt);
        if (mp.getPlacement() == 1) gr.addWinCount();
        else gr.addLoseCount();
    }

    @Transactional
    public void recalculateAllRatings() {
        List<Object[]> pairs = matchRecordRepository.findDistinctRoomBoardGamePairs();
        for (Object[] pair : pairs) {
            Long roomId = ((Number) pair[0]).longValue();
            Long boardGameId = ((Number) pair[1]).longValue();
            recalculateRatings(roomId, boardGameId);
        }
    }
}
