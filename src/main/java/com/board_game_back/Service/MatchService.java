package com.board_game_back.Service;

import com.board_game_back.DTO.MatchDto;
import com.board_game_back.DTO.MatchDto.ResultResponse;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.GlickoStats;
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

            participants.add(new MatchParticipant(matchRecord, member, pr.placement()));
            gameRatings.add(gameRating);
        }

        glicko2Calculator.calculateMultiplayerRatings(calcResults);

        List<MatchDto.ResultResponse> responseList = new ArrayList<>();

        for (int i = 0; i < participants.size(); i++) {
            MatchParticipant participant = participants.get(i);
            Glicko2Calculator.PlayerResult calcResult = calcResults.get(i);
            PlayerGameRating gameRating = gameRatings.get(i);
            Member member = participant.getMember();

            double ratingChange =
                calcResult.newStats.getRating() - gameRating.getGameStats().getRating();
            participant.updateRatingChange(ratingChange);

            gameRating.getGameStats().update(
                calcResult.newStats.getRating(),
                calcResult.newStats.getRatingDeviation(),
                calcResult.newStats.getVolatility()
            );
            gameRating.addPlayCount();

            if (participant.getPlacement() == 1) {
                gameRating.addWinCount();
            } else {
                gameRating.addLoseCount();
            }

            member.getOverallStats().update(
                calcResult.newStats.getRating(),
                calcResult.newStats.getRatingDeviation(),
                calcResult.newStats.getVolatility()
            );

            ratingRepository.save(gameRating);
            memberRepository.save(member);

            responseList.add(new MatchDto.ResultResponse(
                member.getId(), member.getNickname(), participant.getPlacement(), ratingChange
            ));
        }

        matchRecordRepository.save(matchRecord);

        return responseList;
    }

    @Transactional
    public List<MatchDto.MatchHistoryResponse> getMatchHistory(Long roomId) {
        List<MatchRecord> matches = matchRecordRepository.findByRoomIdOrderByPlayedAtDesc(roomId);
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
                        p.getPlacement(),
                        p.getRatingChange()
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

        BoardGame game = match.getBoardGame();

        // 2. 기존 참가자들의 ratingChange를 역산해서 점수 되돌리기
        for (MatchParticipant oldParticipant : match.getParticipants()) {
            Member member = oldParticipant.getMember();
            PlayerGameRating gameRating = ratingRepository
                .findByMemberAndBoardGame(member, game)
                .orElseThrow();

            double oldChange = oldParticipant.getRatingChange();
            GlickoStats stats = gameRating.getGameStats();
            stats.update(
                stats.getRating() - oldChange,
                stats.getRatingDeviation(),
                stats.getVolatility()
            );
            ratingRepository.save(gameRating);
        }

        // 3. 새 순위로 Glicko-2 재계산
        List<Glicko2Calculator.PlayerResult> calcResults = new ArrayList<>();
        List<MatchParticipant> newParticipants = new ArrayList<>();

        for (MatchDto.ParticipantRequest pr : request.participants()) {
            Member member = memberRepository.findById(pr.memberId()).orElseThrow();
            PlayerGameRating gameRating = ratingRepository
                .findByMemberAndBoardGame(member, game)
                .orElseGet(() -> ratingRepository.save(
                    PlayerGameRating.builder().member(member).boardGame(game).room(match.getRoom()).build()
                ));

            calcResults.add(new Glicko2Calculator.PlayerResult(
                member.getId(), pr.placement(), gameRating.getGameStats()
            ));
            newParticipants.add(new MatchParticipant(match, member, pr.placement()));
        }

        glicko2Calculator.calculateMultiplayerRatings(calcResults);

        List<MatchDto.ResultResponse> responseList = new ArrayList<>();

        for (int i = 0; i < newParticipants.size(); i++) {
            MatchParticipant participant = newParticipants.get(i);
            Glicko2Calculator.PlayerResult calcResult = calcResults.get(i);
            Member member = participant.getMember();

            PlayerGameRating gameRating = ratingRepository
                .findByMemberAndBoardGame(member, game).orElseThrow();

            double ratingChange = calcResult.newStats.getRating() - gameRating.getGameStats().getRating();
            participant.updateRatingChange(ratingChange);

            gameRating.getGameStats().update(
                calcResult.newStats.getRating(),
                calcResult.newStats.getRatingDeviation(),
                calcResult.newStats.getVolatility()
            );
            ratingRepository.save(gameRating);

            responseList.add(new MatchDto.ResultResponse(
                member.getId(), member.getNickname(), participant.getPlacement(), ratingChange
            ));
        }

        // 4. 기존 참가자 목록 교체 후 저장
        match.getParticipants().clear();
        match.getParticipants().addAll(newParticipants);
        matchRecordRepository.save(match);

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

                double ratingChange = result.newStats.getRating() - gr.getGameStats().getRating();
                mp.updateRatingChange(ratingChange);

                gr.getGameStats().update(
                    result.newStats.getRating(),
                    result.newStats.getRatingDeviation(),
                    result.newStats.getVolatility()
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
    }
}
