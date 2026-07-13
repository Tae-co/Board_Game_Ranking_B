package com.board_game_back.Controller;

import com.board_game_back.DTO.AuthDto;
import com.board_game_back.Service.AuthService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 전화번호 존재 여부 확인 */
    @PostMapping("/check-phone")
    public ResponseEntity<AuthDto.CheckPhoneResponse> checkPhone(
            @RequestBody AuthDto.CheckPhoneRequest request) {
        boolean exists = authService.checkPhoneExists(request.phoneNumber());
        return ResponseEntity.ok(new AuthDto.CheckPhoneResponse(exists));
    }

    /** 기존 회원 로그인 (전화번호 + 비밀번호) */
    @PostMapping("/login")
    public ResponseEntity<AuthDto.LoginResponse> login(
            @RequestBody AuthDto.LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.phoneNumber(), request.password());
        return ResponseEntity.ok(toLoginResponse(result));
    }

    /** OTP 발송 (신규 회원) */
    @PostMapping("/send-otp")
    public ResponseEntity<String> sendOtp(@RequestBody AuthDto.PhoneRequest request) {
        authService.sendOtp(request.phoneNumber());
        return ResponseEntity.ok("인증번호가 발송되었습니다. (콘솔을 확인하세요)");
    }

    /** OTP 검증 (신규 회원) */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthDto.VerifyOtpResponse> verifyOtp(
            @RequestBody AuthDto.VerifyOtpRequest request) {
        Long memberId = authService.verifyOtp(request.phoneNumber(), request.otpCode());
        return ResponseEntity.ok(new AuthDto.VerifyOtpResponse(memberId));
    }

    /** 신규 회원 가입 완료 (닉네임 + 비밀번호) */
    @PostMapping("/register")
    public ResponseEntity<AuthDto.LoginResponse> register(
            @RequestBody AuthDto.RegisterRequest request) {
        AuthService.LoginResult result = authService.register(
                request.memberId(), request.nickname(), request.password());
        return ResponseEntity.ok(toLoginResponse(result));
    }

    /** 닉네임 로그인 (로그인/회원가입 통합) */
    @PostMapping("/nickname")
    public ResponseEntity<AuthDto.LoginResponse> nicknameLogin(
            @RequestBody AuthDto.NicknameLoginRequest request) {
        AuthService.LoginResult result = authService.nicknameLogin(request.nickname());
        return ResponseEntity.ok(toLoginResponse(result));
    }

    /** 카카오 소셜 로그인 */
    @PostMapping("/kakao")
    public ResponseEntity<AuthDto.LoginResponse> kakaoLogin(
            @RequestBody AuthDto.KakaoLoginRequest request) {
        AuthService.LoginResult result = authService.kakaoLogin(request.kakaoAccessToken());
        return ResponseEntity.ok(toLoginResponse(result));
    }

    /** Apple 소셜 로그인 */
    @PostMapping("/apple")
    public ResponseEntity<AuthDto.LoginResponse> appleLogin(
            @RequestBody AuthDto.AppleLoginRequest request) {
        AuthService.LoginResult result = authService.appleLogin(request.identityToken(), request.nickname());
        return ResponseEntity.ok(toLoginResponse(result));
    }

    /** 관리자 로그인 */
    @PostMapping("/admin-login")
    public ResponseEntity<AuthDto.LoginResponse> adminLogin(
            @RequestBody AuthDto.AdminLoginRequest request) {
        AuthService.LoginResult result = authService.adminLogin(request.username(), request.password());
        return ResponseEntity.ok(toLoginResponse(result));
    }

    /** Access Token 갱신 - body에서 refreshToken 수신 (iOS 쿠키 차단 대응) */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody(required = false) AuthDto.RefreshRequest request) {
        String refreshToken = request != null ? request.refreshToken() : null;
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401).body("Refresh Token이 없습니다.");
        }
        try {
            AuthService.TokenPair tokens = authService.refresh(refreshToken);
            return ResponseEntity.ok(new AuthDto.TokenResponse(tokens.accessToken(), tokens.refreshToken()));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Refresh Token이 만료되었습니다.");
        }
    }

    /** 로그아웃 - 클라이언트에서 localStorage 삭제로 처리 */
    @PostMapping("/logout")
    public ResponseEntity<String> logout() {
        return ResponseEntity.ok("로그아웃 완료");
    }

    /** 닉네임 중복 체크 */
    @GetMapping("/check-nickname")
    public ResponseEntity<Map<String, Boolean>> checkNickname(@RequestParam String nickname) {
        return ResponseEntity.ok(Map.of("available", authService.isNicknameAvailable(nickname)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
    }

    private AuthDto.LoginResponse toLoginResponse(AuthService.LoginResult result) {
        return new AuthDto.LoginResponse(
                result.member().getId(),
                result.member().getNickname(),
                result.member().getRole(),
                result.accessToken(),
                result.refreshToken()
        );
    }
}
