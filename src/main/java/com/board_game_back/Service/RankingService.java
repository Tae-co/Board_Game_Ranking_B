package com.board_game_back.Service;

import com.board_game_back.DTO.RankingDto;
import com.board_game_back.DTO.RankingDto.GameRankingResponse;
import com.board_game_back.Entity.PlayerGameRating;
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

            responseList.add(new RankingDto.GameRankingResponse(
                currentRank++, // 순위를 1씩 증가시키면서 넣음
                rating.getMember().getId(),
                rating.getMember().getNickname(),
                rating.getGameStats().getRating(),
                rating.getPlayCount()
            ));
        }

        return responseList;
    }
}
