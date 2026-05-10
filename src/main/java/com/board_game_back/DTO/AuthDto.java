package com.board_game_back.DTO;

public class AuthDto {

    public record CheckPhoneRequest(String phoneNumber) {}
    public record CheckPhoneResponse(boolean exists) {}

    public record PhoneRequest(String phoneNumber) {}

    public record VerifyOtpRequest(String phoneNumber, String otpCode) {}
    public record VerifyOtpResponse(Long memberId) {}

    public record RegisterRequest(Long memberId, String nickname, String password) {}

    public record LoginRequest(String phoneNumber, String password) {}

    public record LoginResponse(Long memberId, String nickname, String role, String accessToken, String refreshToken) {}

    public record RefreshRequest(String refreshToken) {}

    public record AdminLoginRequest(String username, String password) {}

    public record TokenResponse(String accessToken) {}

    public record KakaoLoginRequest(String kakaoAccessToken) {}

    public record NicknameLoginRequest(String nickname) {}
}
