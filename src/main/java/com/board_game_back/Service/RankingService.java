package com.board_game_back.Service;

import com.board_game_back.DTO.RankingDto;
import com.board_game_back.DTO.RankingDto.GameRankingResponse;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private final PlayerGameRatingRepository ratingRepository;

    public List<GameRankingResponse> getGameRanking(Long boardGameId) {
        List<PlayerGameRating> ratings = ratingRepository.findByBoardGameIdOrderByDisplayScoreDesc(boardGameId);

        List<RankingDto.GameRankingResponse> responseList = new ArrayList<>();
        int currentRank = 1;

        for (PlayerGameRating rating : ratings) {
            if (rating.getPlayCount() == 0) continue;

            responseList.add(new RankingDto.GameRankingResponse(
                currentRank++,
                rating.getMember().getId(),
                rating.getMember().getNickname(),
                rating.getMember().getProfileImage(),
                rating.getGameStats().getDisplayScore(),
                rating.getPlayCount(), rating.getWinCount(), rating.getLoseCount()
            ));
        }

        return responseList;
    }

    public List<GameRankingResponse> getRoomRanking(Long roomId, Long boardGameId) {
        List<PlayerGameRating> ratings = ratingRepository.findByRoomIdAndBoardGameIdOrderByPlayedThenDisplayScore(
            roomId, boardGameId);

        List<RankingDto.GameRankingResponse> responseList = new ArrayList<>();
        int currentRank = 1;

        for (PlayerGameRating rating : ratings) {
            if (rating.getPlayCount() > 0) {
                responseList.add(new RankingDto.GameRankingResponse(
                    currentRank++,
                    rating.getMember().getId(),
                    rating.getMember().getNickname(),
                    rating.getMember().getProfileImage(),
                    rating.getGameStats().getDisplayScore(),
                    rating.getPlayCount(), rating.getWinCount(), rating.getLoseCount()));
            } else {
                responseList.add(new RankingDto.GameRankingResponse(
                    null,
                    rating.getMember().getId(),
                    rating.getMember().getNickname(),
                    rating.getMember().getProfileImage(),
                    rating.getGameStats().getDisplayScore(),
                    rating.getPlayCount(), rating.getWinCount(), rating.getLoseCount()));
            }
        }

        return responseList;
    }
}
