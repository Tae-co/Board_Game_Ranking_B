package com.board_game_back.Service;

import com.board_game_back.DTO.MatchDto;
import com.board_game_back.DTO.MatchDto.ResultResponse;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.MatchParticipant;
import com.board_game_back.Entity.MatchRecord;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomMember;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRecordRepository matchRecordRepository;
    private final PlayerGameRatingRepository ratingRepository;
    private final BoardGameRepository boardGameRepository;
    private final MemberRepository memberRepository;
    private final Glicko2Calculator glicko2Calculator;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;

    @Transactional
    public List<ResultResponse> recordMatchResult(MatchDto.ResultRequest request) {

        BoardGame game = boardGameRepository.findById(request.boardGameId())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게임입니다."));

        Room room = roomRepository.findById(request.roomId())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));

        MatchRecord matchRecord = MatchRecord.builder().boardGame(game).room(room).build();

        List<Glicko2Calculator.PlayerResult> calcResults = new ArrayList<>();
        List<MatchParticipant> participants = new ArrayList<>();
        List<PlayerGameRating> gameRatings = new ArrayList<>();

        for (MatchDto.ParticipantRequest pr : request.participants()) {
            Member member = memberRepository.findById(pr.memberId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

            PlayerGameRating gameRating = ratingRepository.findByMemberAndBoardGameAndRoom(member, game, room)
                .orElseGet(() -> {
                    PlayerGameRating newRating = PlayerGameRating.builder()
                        .member(member)
                        .boardGame(game)
                        .room(room)
                        .build();
                    return ratingRepository.save(newRating);
                });

            calcResults.add(new Glicko2Calculator.PlayerResult(
                member.getId(), pr.placement(), gameRating.getGameStats()
            ));

            MatchParticipant mp = new MatchParticipant(matchRecord, member, pr.placement());
            if (pr.scoresJson() != null) {
                mp.updateScoresJson(pr.scoresJson());
            }
            participants.add(mp);
            gameRatings.add(gameRating);
        }

        glicko2Calculator.calculateMultiplayerRatings(calcResults);

        List<MatchDto.ResultResponse> responseList = new ArrayList<>();

        for (int i = 0; i < participants.size(); i++) {
            MatchParticipant participant = participants.get(i);
            Glicko2Calculator.PlayerResult calcResult = calcResults.get(i);
            PlayerGameRating gameRating = gameRatings.get(i);
            Member member = participant.getMember();

            double ratingChange = calcResult.newStats.getDisplayScore()
                - gameRating.getGameStats().getDisplayScore();
            participant.updateRatingChange(ratingChange);

            double newMu = calcResult.newStats.getRating();
            gameRating.getGameStats().update(
                newMu,
                calcResult.newStats.getRatingDeviation(),
                0.0
            );
            gameRating.addPlayCount();

            if (participant.getPlacement() == 1) {
                gameRating.addWinCount();
            } else {
                gameRating.addLoseCount();
            }

            if (newMu > member.getOverallStats().getRating()) {
                member.getOverallStats().update(newMu,
                    calcResult.newStats.getRatingDeviation(),
                    0.0);
                memberRepository.save(member);
            }

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
            ? matchRecordRepository.findByRoomIdAndBoardGameIdOrderByPlayedAtDesc(roomId, room.getBoardGameId())
            : matchRecordRepository.findByRoomIdOrderByPlayedAtDesc(roomId);
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
    public List<MatchDto.ResultResponse> updateMatchResult(Long matchId, MatchDto.ResultRequest request) {

        // 1. 기존 매치 조회
        MatchRecord match = matchRecordRepository.findById(matchId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 매치입니다."));

        Long roomId = match.getRoom().getId();
        Long boardGameId = match.getBoardGame().getId();

        // 2. 새 participants 생성
        List<MatchParticipant> newParticipants = new ArrayList<>();
        for (MatchDto.ParticipantRequest pr : request.participants()) {
            Member member = memberRepository.findById(pr.memberId()).orElseThrow();
            MatchParticipant newMp = new MatchParticipant(match, member, pr.placement());
            if (pr.scoresJson() != null) {
                newMp.updateScoresJson(pr.scoresJson());
            }
            newParticipants.add(newMp);
        }

        // 3. participants 교체 후 저장
        match.getParticipants().clear();
        match.getParticipants().addAll(newParticipants);
        matchRecordRepository.save(match);

        // 4. 처음부터 전체 재계산 (롤백 없이 정확한 Glicko-2 적용)
        recalculateRatings(roomId, boardGameId);

        // 5. 재계산 결과의 ratingChange로 응답 생성
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
        if (!"HOST".equals(roomMember.getRole())) {
            throw new SecurityException("방장만 삭제할 수 있습니다.");
        }

        matchRecordRepository.delete(match);
        recalculateRatings(roomId, boardGameId);
    }

    private void recalculateRatings(Long roomId, Long boardGameId) {
        Room room = roomRepository.findById(roomId).orElseThrow();
        BoardGame game = boardGameRepository.findById(boardGameId).orElseThrow();

        // 1. 해당 방/게임의 모든 rating 초기화
        List<PlayerGameRating> allRatings = ratingRepository
            .findByRoomIdAndBoardGameIdOrderByGameStatsRatingDesc(roomId, boardGameId);
        Map<Long, PlayerGameRating> ratingByMemberId = new HashMap<>();
        for (PlayerGameRating gr : allRatings) {
            gr.reset();
            ratingByMemberId.put(gr.getMember().getId(), gr);
        }

        // 2. 시간순 매치 재계산
        List<MatchRecord> matches = matchRecordRepository
            .findByRoomIdAndBoardGameIdOrderByPlayedAtAsc(roomId, boardGameId);

        for (MatchRecord match : matches) {
            List<Glicko2Calculator.PlayerResult> calcResults = new ArrayList<>();
            List<MatchParticipant> participants = match.getParticipants();

            for (MatchParticipant mp : participants) {
                Long memberId = mp.getMember().getId();
                PlayerGameRating gr = ratingByMemberId.computeIfAbsent(memberId, id -> {
                    PlayerGameRating nr = PlayerGameRating.builder()
                        .member(mp.getMember()).boardGame(game).room(room).build();
                    return ratingRepository.save(nr);
                });
                calcResults.add(new Glicko2Calculator.PlayerResult(
                    memberId, mp.getPlacement(), gr.getGameStats()
                ));
            }

            glicko2Calculator.calculateMultiplayerRatings(calcResults);

            for (int i = 0; i < participants.size(); i++) {
                MatchParticipant mp = participants.get(i);
                Glicko2Calculator.PlayerResult result = calcResults.get(i);
                Long memberId = mp.getMember().getId();
                PlayerGameRating gr = ratingByMemberId.get(memberId);

                double ratingChange = result.newStats.getDisplayScore()
                    - gr.getGameStats().getDisplayScore();
                mp.updateRatingChange(ratingChange);

                gr.getGameStats().update(
                    result.newStats.getRating(),
                    result.newStats.getRatingDeviation(),
                    0.0
                );
                gr.addPlayCount();
                if (mp.getPlacement() == 1) {
                    gr.addWinCount();
                } else {
                    gr.addLoseCount();
                }
            }
        }

        ratingRepository.saveAll(ratingByMemberId.values());

        // 방을 나간 멤버의 PlayerGameRating 삭제 (점수판에서 제거)
        Set<Long> currentMemberIds = roomMemberRepository.findByRoomId(roomId)
            .stream().map(rm -> rm.getMember().getId()).collect(Collectors.toSet());
        List<PlayerGameRating> leftMemberRatings = ratingByMemberId.values().stream()
            .filter(gr -> !currentMemberIds.contains(gr.getMember().getId()))
            .collect(Collectors.toList());
        if (!leftMemberRatings.isEmpty()) {
            ratingRepository.deleteAll(leftMemberRatings);
        }

        // member.overallStats를 display score 기준 최고 게임의 μ/σ로 갱신
        for (PlayerGameRating gr : ratingByMemberId.values()) {
            Member member = gr.getMember();
            ratingRepository.findPlayedByMemberId(member.getId())
                .stream()
                .max(Comparator.comparingDouble(r -> r.getGameStats().getDisplayScore()))
                .ifPresent(best -> {
                    member.getOverallStats().update(
                        best.getGameStats().getRating(),
                        best.getGameStats().getRatingDeviation(),
                        0.0);
                    memberRepository.save(member);
                });
        }
    }

    @Transactional
    public void recalculateAllRatings() {
        List<Object[]> pairs = matchRecordRepository.findDistinctRoomBoardGamePairs();
        for (Object[] pair : pairs) {
            Long roomId = (Long) pair[0];
            Long boardGameId = (Long) pair[1];
            recalculateRatings(roomId, boardGameId);
        }
    }
}
