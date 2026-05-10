package com.board_game_back.Service;

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

    @Transactional
    public List<ResultResponse> recordMatchResult(MatchDto.ResultRequest request, Long requesterId) {
        roomMemberRepository.findByRoomIdAndMemberId(request.roomId(), requesterId)
            .orElseThrow(() -> new SecurityException("방 멤버만 매치를 등록할 수 있습니다."));

        BoardGame game = boardGameRepository.findById(request.boardGameId())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게임입니다."));

        Room room = roomRepository.findById(request.roomId())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));

        MatchRecord matchRecord = MatchRecord.builder().boardGame(game).room(room).build();

        List<RatingCalculator.PlayerResult> calcResults = new ArrayList<>();
        List<MatchParticipant> participants = new ArrayList<>();
        List<PlayerGameRating> gameRatings = new ArrayList<>();

        for (MatchDto.ParticipantRequest pr : request.participants()) {
            Member member = memberRepository.findById(pr.memberId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

            PlayerGameRating gameRating = ratingRepository.findByMemberAndBoardGameAndRoom(member, game, room)
                .orElseGet(() -> ratingRepository.save(
                    PlayerGameRating.builder().member(member).boardGame(game).room(room).build()
                ));

            calcResults.add(new RatingCalculator.PlayerResult(
                member.getId(), pr.placement(), gameRating.getGameStats()
            ));

            MatchParticipant mp = new MatchParticipant(matchRecord, member, pr.placement());
            if (pr.scoresJson() != null) mp.updateScoresJson(pr.scoresJson());
            participants.add(mp);
            gameRatings.add(gameRating);
        }

        ratingCalculator.calculateMultiplayerRatings(calcResults);

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
                m.getPlayedAt().toString(),
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
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 매치입니다."));

        Long roomId = match.getRoom().getId();
        RoomMember rm = roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId)
            .orElseThrow(() -> new IllegalArgumentException("방 멤버가 아닙니다."));
        if (rm.getRole() != MemberRole.HOST) {
            throw new SecurityException("방장만 매치를 수정할 수 있습니다.");
        }

        Long boardGameId = match.getBoardGame().getId();

        List<MatchParticipant> newParticipants = new ArrayList<>();
        for (MatchDto.ParticipantRequest pr : request.participants()) {
            Member member = memberRepository.findById(pr.memberId()).orElseThrow();
            MatchParticipant newMp = new MatchParticipant(match, member, pr.placement());
            if (pr.scoresJson() != null) newMp.updateScoresJson(pr.scoresJson());
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
            .orElseThrow(() -> new IllegalArgumentException("매치를 찾을 수 없습니다."));

        if (match.getRoom() == null) {
            throw new IllegalArgumentException("방 정보가 없는 매치는 삭제할 수 없습니다.");
        }

        Long roomId = match.getRoom().getId();
        Long boardGameId = match.getBoardGame().getId();

        RoomMember roomMember = roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId)
            .orElseThrow(() -> new IllegalArgumentException("방 멤버가 아닙니다."));
        if (roomMember.getRole() != MemberRole.HOST) {
            throw new SecurityException("방장만 삭제할 수 있습니다.");
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

        for (MatchRecord match : matches) {
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
                    memberId, mp.getPlacement(), gr.getGameStats()
                ));
            }

            if (calcResults.size() < 2) continue;

            ratingCalculator.calculateMultiplayerRatings(calcResults);

            for (int i = 0; i < participants.size(); i++) {
                MatchParticipant mp = participants.get(i);
                RatingCalculator.PlayerResult result = calcResults.get(i);
                Long memberId = mp.getMember().getId();
                PlayerGameRating gr = ratingByMemberId.get(memberId);

                double ratingChange = result.newStats.getDisplayScore() - gr.getGameStats().getDisplayScore();
                mp.updateRatingChange(ratingChange);

                gr.getGameStats().update(result.newStats.getRating(), result.newStats.getRatingDeviation(), 0.0);
                gr.addPlayCount();
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
            List<PlayerGameRating> allPlayed = ratingRepository.findPlayedByMemberId(member.getId());
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
