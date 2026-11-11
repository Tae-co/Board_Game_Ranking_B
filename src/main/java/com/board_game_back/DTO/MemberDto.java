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
        double overallRd,
        String profileImage
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

    // 📤 [Response] 관리자 회원 목록 한 줄. 값이 없으면 빈 문자열 (Map으로 내보내던 때와 같음)
    public record AdminSummary(
        Long memberId,
        String nickname,
        String role,
        String phoneNumber
    ) {
        public static AdminSummary from(com.board_game_back.Entity.Member m) {
            return new AdminSummary(
                m.getId(),
                m.getNickname() != null ? m.getNickname() : "",
                m.getRole() != null ? m.getRole() : "",
                m.getPhoneNumber() != null ? m.getPhoneNumber() : ""
            );
        }
    }

}
