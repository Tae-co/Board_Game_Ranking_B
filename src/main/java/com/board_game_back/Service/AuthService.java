package com.board_game_back.Service;

import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Security.JwtTokenProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    // OTP 임시 저장 (실제 환경에서는 Redis 사용 권장)
    private final Map<String, String> otpStorage = new HashMap<>();

    public record LoginResult(Member member, String accessToken, String refreshToken) {}

    /** 전화번호 존재 여부 확인 */
    public boolean checkPhoneExists(String phoneNumber) {
        return memberRepository.findByPhoneNumber(phoneNumber).isPresent();
    }

    /** 기존 회원 로그인 (전화번호 + 비밀번호) */
    @Transactional(readOnly = true)
    public LoginResult login(String phoneNumber, String password) {
        Member member = memberRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 전화번호입니다."));

        if (member.getPassword() == null) {
            throw new IllegalStateException("비밀번호가 설정되지 않은 계정입니다. OTP로 재인증 후 비밀번호를 설정해주세요.");
        }

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 틀렸습니다.");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getId());
        return new LoginResult(member, accessToken, refreshToken);
    }

    /** OTP 발송 */
    public void sendOtp(String phoneNumber) {
        String otpCode = String.format("%06d", new Random().nextInt(1000000));
        otpStorage.put(phoneNumber, otpCode);
        System.out.println("전화번호 [" + phoneNumber + "] 인증번호: " + otpCode);
    }

    /** OTP 검증 - 신규 회원용 (memberId 반환) */
    @Transactional
    public Long verifyOtp(String phoneNumber, String otpCode) {
        String saved = otpStorage.get(phoneNumber);
        if (saved == null || !saved.equals(otpCode)) {
            throw new IllegalArgumentException("인증번호가 틀렸거나 만료되었습니다.");
        }
        otpStorage.remove(phoneNumber);

        // 신규 회원이면 생성, 기존 회원이면 반환
        return memberRepository.findByPhoneNumber(phoneNumber)
                .orElseGet(() -> memberRepository.save(
                        Member.builder()
                                .phoneNumber(phoneNumber)
                                .role("USER")
                                .build()
                )).getId();
    }

    /** 신규 회원 등록 (닉네임 + 비밀번호 설정) */
    @Transactional
    public LoginResult register(Long memberId, String nickname, String rawPassword) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        member.updateNickname(nickname);
        member.updatePassword(passwordEncoder.encode(rawPassword));

        String accessToken = jwtTokenProvider.generateAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getId());
        return new LoginResult(member, accessToken, refreshToken);
    }

    /** 관리자 로그인 */
    @Transactional(readOnly = true)
    public LoginResult adminLogin(String username, String password) {
        Member admin = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 틀렸습니다."));

        if (!"ADMIN".equals(admin.getRole())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 틀렸습니다.");
        }

        if (!passwordEncoder.matches(password, admin.getPassword())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 틀렸습니다.");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(admin.getId(), admin.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(admin.getId());
        return new LoginResult(admin, accessToken, refreshToken);
    }

    /** Access Token 갱신 */
    public String refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다.");
        }
        Long memberId = jwtTokenProvider.getMemberIdFromToken(refreshToken);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        return jwtTokenProvider.generateAccessToken(member.getId(), member.getRole());
    }

    /** 닉네임 중복 체크 */
    public boolean isNicknameAvailable(String nickname) {
        return !memberRepository.existsByNickname(nickname);
    }

    /** 비밀번호 변경 */
    @Transactional
    public void changePassword(Long memberId, String currentPassword, String newPassword) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 틀렸습니다.");
        }
        member.updatePassword(passwordEncoder.encode(newPassword));
        memberRepository.save(member);
    }
}
