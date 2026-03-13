package com.board_game_back.Service;

import com.board_game_back.DTO.RankingDto;
import com.board_game_back.DTO.RankingDto.GameRankingResponse;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 데이터를 읽기만 하므로 readOnly 옵션으로 조회 성능을 높입니다.
public class RankingService {

    private final PlayerGameRatingRepository ratingRepository;
    private final MemberRepository memberRepository;


    // 특정 보드게임의 리더보드 데이터 가져오기
    public List<GameRankingResponse> getGameRanking(Long boardGameId) {

        // 1. DB에서 특정 게임의 랭킹 데이터를 점수(Rating) 내림차순으로 싹 다 가져옵니다.
        // (아까 Repository 만들 때 미리 짜둔 쿼리 메서드입니다!)
        List<PlayerGameRating> ratings = ratingRepository.findByBoardGameIdOrderByGameStatsRatingDesc(
            boardGameId);

        // 2. React로 보낼 빈 DTO 리스트를 준비합니다.
        List<RankingDto.GameRankingResponse> responseList = new ArrayList<>();
        int currentRank = 1; // 1등부터 시작

        // 3. Entity 데이터를 DTO 택배 상자로 하나씩 옮겨 담으면서 순위(Rank)를 매겨줍니다.
        for (PlayerGameRating rating : ratings) {

            // (선택) 0판 한 사람(점수만 만들어진 사람)은 랭킹판에 안 보이게 필터링
            if (rating.getPlayCount() == 0) {
                continue;
            }

            responseList.add(
                new RankingDto.GameRankingResponse(currentRank++, rating.getMember().getId(),
                    rating.getMember().getNickname(), rating.getGameStats().getRating(),
                    rating.getPlayCount(), rating.getWinCount(),    // 추가
                    rating.getLoseCount()    // 추가
                ));
        }

        return responseList;
    }

    public List<RankingDto.GlobalRankingResponse> getGlobalRanking() {
        List<Member> members = memberRepository.findAllByOrderByOverallStatsRatingDesc();

        List<RankingDto.GlobalRankingResponse> responseList = new ArrayList<>();
        int currentRank = 1;

        for (Member member : members) {
            if (member.getOverallStats() == null || member.getOverallStats().getRating() == 1500.0) continue;

            responseList.add(new RankingDto.GlobalRankingResponse(
                currentRank++,
                member.getId(),
                member.getNickname(),
                member.getOverallStats().getRating()
            ));
        }
        return responseList;
    }

    // 방별 특정 보드게임 랭킹 조회 (플레이한 멤버 먼저, 미플레이 멤버 포함)
    public List<GameRankingResponse> getRoomRanking(Long roomId, Long boardGameId) {
        List<PlayerGameRating> ratings = ratingRepository.findByRoomIdAndBoardGameIdOrderByPlayedThenRating(
            roomId, boardGameId);

        List<RankingDto.GameRankingResponse> responseList = new ArrayList<>();
        int currentRank = 1;

        for (PlayerGameRating rating : ratings) {
            if (rating.getPlayCount() > 0) {
                responseList.add(
                    new RankingDto.GameRankingResponse(currentRank++, rating.getMember().getId(),
                        rating.getMember().getNickname(), rating.getGameStats().getRating(),
                        rating.getPlayCount(), rating.getWinCount(), rating.getLoseCount()));
            } else {
                // 미플레이 멤버: rank = null
                responseList.add(
                    new RankingDto.GameRankingResponse(null, rating.getMember().getId(),
                        rating.getMember().getNickname(), rating.getGameStats().getRating(),
                        rating.getPlayCount(), rating.getWinCount(), rating.getLoseCount()));
            }
        }

        return responseList;
    }


}
