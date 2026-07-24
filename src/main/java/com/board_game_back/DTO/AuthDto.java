package com.board_game_back.DTO;

public class AuthDto {

    public record LoginResponse(Long memberId, String nickname, String role, String accessToken, String refreshToken) {}

    public record RefreshRequest(String refreshToken) {}

    public record TokenResponse(String accessToken, String refreshToken) {}

    public record AppleLoginRequest(String identityToken, String nickname) {}
}
