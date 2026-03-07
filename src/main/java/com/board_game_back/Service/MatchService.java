package com.board_game_back.Service;

import com.board_game_back.DTO.MatchDto;
import com.board_game_back.DTO.MatchDto.ResultResponse;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.MatchParticipant;
import com.board_game_back.Entity.MatchRecord;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
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

    @Transactional
    public List<ResultResponse> recordMatchResult(MatchDto.ResultRequest request) {

        // 1. 게임 정보 조회
        BoardGame game = boardGameRepository.findById(request.boardGameId())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게임입니다."));

        Room room = roomRepository.findById(request.roomId())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));

        // 2. 매치 기록 생성
        MatchRecord matchRecord = MatchRecord.builder().boardGame(game).build();

        List<Glicko2Calculator.PlayerResult> calcResults = new ArrayList<>();
        List<MatchParticipant> participants = new ArrayList<>();
        List<PlayerGameRating> gameRatings = new ArrayList<>(); // 🔴 버그 수정: 이중조회 제거

        // 3. 참가자 데이터 수집 (gameRating을 여기서 한 번만 조회)
        for (MatchDto.ParticipantRequest pr : request.participants()) {
            Member member = memberRepository.findById(pr.memberId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

            // 개별 게임 랭킹 찾기 (없으면 새로 생성 후 바로 저장)
            PlayerGameRating gameRating = ratingRepository.findByMemberAndBoardGameAndRoom(member, game, room)
                .orElseGet(() -> {
                    PlayerGameRating newRating = PlayerGameRating.builder()
                        .member(member)
                        .boardGame(game)
                        .room(room)
                        .build();
                    return ratingRepository.save(newRating); // 🔴 버그 수정: 즉시 저장
                });

            calcResults.add(new Glicko2Calculator.PlayerResult(
                member.getId(), pr.placement(), gameRating.getGameStats()
            ));

            participants.add(new MatchParticipant(matchRecord, member, pr.placement()));
            gameRatings.add(gameRating); // 리스트에 보관
        }

        // 4. Glicko-2 계산
        glicko2Calculator.calculateMultiplayerRatings(calcResults);

        List<MatchDto.ResultResponse> responseList = new ArrayList<>();

        // 5. 계산된 점수 DB 업데이트 (이중조회 없이 위에서 모아둔 리스트 사용)
        for (int i = 0; i < participants.size(); i++) {
            MatchParticipant participant = participants.get(i);
            Glicko2Calculator.PlayerResult calcResult = calcResults.get(i);
            PlayerGameRating gameRating = gameRatings.get(i); // 🔴 버그 수정: 재조회 없이 사용
            Member member = participant.getMember();

            // 점수 변화량 계산
            double ratingChange =
                calcResult.newStats.getRating() - gameRating.getGameStats().getRating();
            participant.updateRatingChange(ratingChange);

            // 개별 랭킹 업데이트
            gameRating.getGameStats().update(
                calcResult.newStats.getRating(),
                calcResult.newStats.getRatingDeviation(),
                calcResult.newStats.getVolatility()
            );
            gameRating.addPlayCount();

            // 🔴 winCount/loseCount 업데이트 (1등이면 승리, 나머지는 패배)
            if (participant.getPlacement() == 1) {
                gameRating.addWinCount();
            } else {
                gameRating.addLoseCount();
            }

            // 🔴 종합 랭킹 업데이트 (TODO 해결)
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

        // 6. 매치 기록 저장
        matchRecordRepository.save(matchRecord);

        return responseList;
    }
}