package com.board_game_back.DTO;

public class AuthDto {

    // 1. 인증번호 발송 요청
    public record PhoneRequest(String phoneNumber) {

    }

    // 2. 인증번호 확인 및 로그인 요청
    public record LoginRequest(String phoneNumber, String otpCode) {

    }

    // 3. 로그인 성공 응답
    public record LoginResponse(Long memberId, String nickname, String token) {

    }
}
