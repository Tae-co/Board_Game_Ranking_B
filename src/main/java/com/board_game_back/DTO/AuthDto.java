package com.board_game_back.DTO;

public class AuthDto {

    // 전화번호 존재 여부 확인
    public record CheckPhoneRequest(String phoneNumber) {}
    public record CheckPhoneResponse(boolean exists) {}

    // OTP 발송
    public record PhoneRequest(String phoneNumber) {}

    // OTP 검증 (신규 회원)
    public record VerifyOtpRequest(String phoneNumber, String otpCode) {}
    public record VerifyOtpResponse(Long memberId) {}

    // 신규 회원 가입 (닉네임 + 비밀번호 설정)
    public record RegisterRequest(Long memberId, String nickname, String password) {}

    // 기존 회원 로그인 (전화번호 + 비밀번호)
    public record LoginRequest(String phoneNumber, String password) {}

    // 로그인 성공 응답
    public record LoginResponse(Long memberId, String nickname, String role, String accessToken) {}

    // 관리자 로그인
    public record AdminLoginRequest(String username, String password) {}

    // 토큰 갱신 응답
    public record TokenResponse(String accessToken) {}

    // 비밀번호 변경
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}
}
