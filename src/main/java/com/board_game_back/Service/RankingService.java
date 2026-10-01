package com.board_game_back.Service;

import com.board_game_back.DTO.RankingDto;
import com.board_game_back.DTO.RankingDto.GameRankingResponse;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Repository.MatchParticipantRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private final PlayerGameRatingRepository ratingRepository;
    private final MatchParticipantRepository participantRepository;
    private final RoomSeasonService roomSeasonService;

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
                rating.getPlayCount(), rating.getWinCount(), rating.getLoseCount(), null
            ));
        }

        return responseList;
    }

    public List<GameRankingResponse> getRoomRanking(Long roomId, Long boardGameId) {
        List<PlayerGameRating> ratings = ratingRepository.findByRoomIdAndBoardGameIdOrderByPlayedThenDisplayScore(
            roomId, boardGameId);
        // 점수와 같은 범위(현재 시즌)의 경기만 센다. null = 리셋된 적 없는 방 → 전 기간
        Map<Long, List<Integer>> placements = participantRepository.placementCountsByMember(
            roomId, boardGameId, roomSeasonService.currentSeasonStartUtc(roomId), null);

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
                    rating.getPlayCount(), rating.getWinCount(), rating.getLoseCount(),
                    placements.get(rating.getMember().getId())));
            } else {
                responseList.add(new RankingDto.GameRankingResponse(
                    null,
                    rating.getMember().getId(),
                    rating.getMember().getNickname(),
                    rating.getMember().getProfileImage(),
                    rating.getGameStats().getDisplayScore(),
                    rating.getPlayCount(), rating.getWinCount(), rating.getLoseCount(),
                    placements.get(rating.getMember().getId())));
            }
        }

        return responseList;
    }
}
