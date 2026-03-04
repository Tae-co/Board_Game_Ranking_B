package com.board_game_back.Controller;

import com.board_game_back.DTO.AuthDto;
import com.board_game_back.Entity.Member;
import com.board_game_back.Service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    // 인증번호 요청
    @PostMapping("/send-otp")
    public ResponseEntity<String> sendOtp(@RequestBody AuthDto.PhoneRequest request) {
        authService.sendOtp(request.phoneNumber());
        return ResponseEntity.ok("인증번호가 발송되었습니다. (콘솔을 확인하세요)");
    }

    // 로그인 확인
    @PostMapping("/login")
    public ResponseEntity<AuthDto.LoginResponse> login(@RequestBody AuthDto.LoginRequest request) {
        Member member = authService.login(request.phoneNumber(), request.otpCode());
        // 실제로는 여기서 JWT 토큰을 생성해 보내야 하지만, 우선 ID만 보냅니다.
        return ResponseEntity.ok(new AuthDto.LoginResponse(member.getId(), member.getNickname(), "dummy-token"));
    }
}
