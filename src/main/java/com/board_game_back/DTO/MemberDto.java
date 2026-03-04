package com.board_game_back.DTO;

public class MemberDto {
    // 📩 [Request] React -> Spring: 유저 생성(회원가입) 요청
    public record SignUpRequest(
        String username,
        String nickname
    ) {}

    // 📤 [Response] Spring -> React: 유저 프로필 및 종합 랭킹 응답
    public record ProfileResponse(
        Long memberId,
        String nickname,
        double overallRating,
        double overallRd
    ) {}

}
