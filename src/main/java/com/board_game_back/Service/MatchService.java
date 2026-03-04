package com.board_game_back.Service;

import com.board_game_back.DTO.MatchDto;
import com.board_game_back.DTO.MatchDto.ResultResponse;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.MatchParticipant;
import com.board_game_back.Entity.MatchRecord;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
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

    @Transactional
    public List<ResultResponse> recordMatchResult(MatchDto.ResultRequest request) {

        // 1. 게임 정보 조회
        BoardGame game = boardGameRepository.findById(request.boardGameId())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게임입니다."));

        // 2. 매치 기록(MatchRecord) 생성
        MatchRecord matchRecord = MatchRecord.builder().boardGame(game).build();

        List<Glicko2Calculator.PlayerResult> calcResults = new ArrayList<>();
        List<MatchParticipant> participants = new ArrayList<>();

        // 3. 참가자 데이터 수집
        for (MatchDto.ParticipantRequest pr : request.participants()) {
            Member member = memberRepository.findById(pr.memberId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

            // 개별 게임 랭킹 찾기 (없으면 새로 생성)
            PlayerGameRating gameRating = ratingRepository.findByMemberAndBoardGame(member, game)
                .orElseGet(() -> new PlayerGameRating(member, game));

            // 계산기용 데이터 준비
            calcResults.add(new Glicko2Calculator.PlayerResult(member.getId(), pr.placement(),
                gameRating.getGameStats()));

            // DB 저장용 참가자 기록 생성
            participants.add(new MatchParticipant(matchRecord, member, pr.placement()));
        }

        // 4. Glicko-2 알고리즘으로 새로운 점수 계산
        glicko2Calculator.calculateMultiplayerRatings(calcResults);

        List<MatchDto.ResultResponse> responseList = new ArrayList<>();

        // 5. 계산된 새 점수를 DB에 업데이트 및 응답 데이터 만들기
        for (int i = 0; i < participants.size(); i++) {
            MatchParticipant participant = participants.get(i);
            Glicko2Calculator.PlayerResult calcResult = calcResults.get(i);

            Member member = participant.getMember();
            PlayerGameRating gameRating = ratingRepository.findByMemberAndBoardGame(member, game)
                .orElseGet(() -> {
                    // 데이터가 없으면(처음 하는 게임이면) 새로 만들어서 저장
                    PlayerGameRating newRating = new PlayerGameRating(member, game);
                    return ratingRepository.save(newRating);
                });

            // 점수 변화량 계산 (+15.2, -8.4 등)
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

            // TODO: 종합 랭킹(member.getOverallStats())도 업데이트하는 로직 추가

            ratingRepository.save(gameRating);

            // 프론트엔드로 보낼 응답 생성
            responseList.add(new MatchDto.ResultResponse(
                member.getId(), member.getNickname(), participant.getPlacement(), ratingChange
            ));
        }

        // 6. 매치 기록 최종 저장 (Cascade 설정으로 인해 Participant들도 같이 저장됨)
        matchRecordRepository.save(matchRecord);

        return responseList;
    }

}
