package com.board_game_back.Controller;

import com.board_game_back.DTO.AuthDto;
import com.board_game_back.Security.JwtTokenProvider;
import com.board_game_back.Service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

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
            @RequestBody AuthDto.LoginRequest request,
            HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request.phoneNumber(), request.password());
        setRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok(new AuthDto.LoginResponse(
                result.member().getId(),
                result.member().getNickname(),
                result.member().getRole(),
                result.accessToken()
        ));
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
            @RequestBody AuthDto.RegisterRequest request,
            HttpServletResponse response) {
        AuthService.LoginResult result = authService.register(
                request.memberId(), request.nickname(), request.password());
        setRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok(new AuthDto.LoginResponse(
                result.member().getId(),
                result.member().getNickname(),
                result.member().getRole(),
                result.accessToken()
        ));
    }

    /** 관리자 로그인 */
    @PostMapping("/admin-login")
    public ResponseEntity<AuthDto.LoginResponse> adminLogin(
            @RequestBody AuthDto.AdminLoginRequest request,
            HttpServletResponse response) {
        AuthService.LoginResult result = authService.adminLogin(request.username(), request.password());
        setRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok(new AuthDto.LoginResponse(
                result.member().getId(),
                result.member().getNickname(),
                result.member().getRole(),
                result.accessToken()
        ));
    }

    /** Access Token 갱신 */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null) {
            return ResponseEntity.status(401).body("Refresh Token이 없습니다.");
        }
        try {
            String newAccessToken = authService.refresh(refreshToken);
            return ResponseEntity.ok(new AuthDto.TokenResponse(newAccessToken));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Refresh Token이 만료되었습니다.");
        }
    }

    /** 로그아웃 */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return ResponseEntity.ok("로그아웃 완료");
    }

    /** 비밀번호 변경 */
    @PutMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AuthDto.ChangePasswordRequest request) {
        String token = authHeader.replace("Bearer ", "");
        Long memberId = jwtTokenProvider.getMemberIdFromToken(token);
        authService.changePassword(memberId, request.currentPassword(), request.newPassword());
        return ResponseEntity.ok("비밀번호가 변경되었습니다.");
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

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(604800);
        response.addCookie(cookie);
    }
}
