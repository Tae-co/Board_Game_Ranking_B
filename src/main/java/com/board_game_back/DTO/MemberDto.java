package com.board_game_back.DTO;

import java.util.List;

public class MemberDto {

    // 📩 [Request] React -> Spring: 유저 생성(회원가입) 요청
    public record SignUpRequest(
        String username,
        String nickname
    ) {

    }

    // 📤 [Response] Spring -> React: 유저 프로필 및 종합 랭킹 응답
    public record ProfileResponse(
        Long memberId,
        String nickname,
        double overallRating,
        double overallRd
    ) {}

    // 📤 [Response] 게임별 통계 항목
    public record GameStatItem(
        String gameName,
        int playCount,
        int winCount,
        int loseCount
    ) {}

    // 📤 [Response] 멤버 전체 플레이 통계
    public record StatsResponse(
        int totalPlay,
        int totalWin,
        int totalLose,
        List<GameStatItem> games
    ) {}

}
