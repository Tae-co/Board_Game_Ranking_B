package com.board_game_back.Service;

import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Security.AppleTokenVerifier;
import com.board_game_back.Security.JwtTokenProvider;
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
    private final AppleTokenVerifier appleTokenVerifier;

    public record LoginResult(Member member, String accessToken, String refreshToken) {}

    public record TokenPair(String accessToken, String refreshToken) {}

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

    /** Access/Refresh Token 갱신 - Refresh Token도 새로 발급해 활성 사용자의 세션 만료를 연장한다 */
    public TokenPair refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다.");
        }
        Long memberId = jwtTokenProvider.getMemberIdFromToken(refreshToken);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        return new TokenPair(
                jwtTokenProvider.generateAccessToken(member.getId(), member.getRole()),
                jwtTokenProvider.generateRefreshToken(member.getId())
        );
    }

    /** 닉네임 중복 체크 */
    public boolean isNicknameAvailable(String nickname) {
        return !memberRepository.existsByNickname(nickname);
    }

    /** OAuth2 콜백용: socialId로 회원 조회 또는 신규 생성 */
    @Transactional
    public Member findOrCreateOAuthMember(String socialId, String nickname) {
        return memberRepository.findBySocialId(socialId).orElseGet(() -> {
            String uniqueNickname = nickname;
            if (memberRepository.existsByNickname(uniqueNickname)) {
                uniqueNickname = nickname + "_" + (System.currentTimeMillis() % 10000);
            }
            return memberRepository.save(
                Member.builder()
                    .socialId(socialId)
                    .nickname(uniqueNickname)
                    .role("USER")
                    .build()
            );
        });
    }

    /** Apple 소셜 로그인 */
    @Transactional
    public LoginResult appleLogin(String identityToken, String nickname) {
        String sub = appleTokenVerifier.verifyAndGetSub(identityToken);
        Member member = findOrCreateOAuthMember("APPLE_" + sub, nickname);
        String accessToken = jwtTokenProvider.generateAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getId());
        return new LoginResult(member, accessToken, refreshToken);
    }

    /** 닉네임 중복 시 랜덤 숫자 붙여 고유 닉네임 생성 */
    private String resolveUniqueNickname(String base) {
        if (!memberRepository.existsByNickname(base)) return base;
        String candidate;
        do {
            candidate = base + "_" + (1000 + new Random().nextInt(9000));
        } while (memberRepository.existsByNickname(candidate));
        return candidate;
    }

}
