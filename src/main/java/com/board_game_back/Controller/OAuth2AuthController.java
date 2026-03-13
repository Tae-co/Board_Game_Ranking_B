package com.board_game_back.Controller;

import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class OAuth2AuthController {

    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${GOOGLE_CLIENT_ID:}")
    private String googleClientId;

    @Value("${GOOGLE_CLIENT_SECRET:}")
    private String googleClientSecret;

    @Value("${FRONTEND_URL:https://boardup.pages.dev}")
    private String frontendUrl;

    @Value("${BACKEND_URL:https://meeple-production.up.railway.app}")
    private String backendUrl;

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USER_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    /** 구글 인증 페이지로 리다이렉트 */
    @GetMapping("/google")
    public void googleLogin(HttpServletResponse response) throws IOException {
        String redirectUri = backendUrl + "/api/auth/google/callback";
        String url = GOOGLE_AUTH_URL
                + "?client_id=" + googleClientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=profile+email";
        response.sendRedirect(url);
    }

    /** 구글 OAuth2 콜백 */
    @GetMapping("/google/callback")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void googleCallback(@RequestParam String code, HttpServletResponse response) throws IOException {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String redirectUri = backendUrl + "/api/auth/google/callback";

            // 1. 토큰 교환
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("client_id", googleClientId);
            params.add("client_secret", googleClientSecret);
            params.add("redirect_uri", redirectUri);
            params.add("code", code);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(
                    GOOGLE_TOKEN_URL, new HttpEntity<>(params, headers), Map.class);
            String accessToken = (String) tokenResponse.getBody().get("access_token");

            // 2. 사용자 정보 조회
            HttpHeaders userHeaders = new HttpHeaders();
            userHeaders.setBearerAuth(accessToken);
            ResponseEntity<Map> userResponse = restTemplate.exchange(
                    GOOGLE_USER_URL, HttpMethod.GET, new HttpEntity<>(userHeaders), Map.class);
            Map<String, Object> userBody = userResponse.getBody();

            String socialId = "GOOGLE_" + userBody.get("sub");
            String nickname = (String) userBody.getOrDefault("name", "구글유저");

            redirectWithToken(response, socialId, nickname);
        } catch (Exception e) {
            response.sendRedirect(frontendUrl + "/login?error=google");
        }
    }


    private void redirectWithToken(HttpServletResponse response, String socialId, String nickname) throws IOException {
        Member member = memberRepository.findBySocialId(socialId)
                .orElseGet(() -> {
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

        String jwtAccessToken = jwtTokenProvider.generateAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getId());
        setRefreshTokenCookie(response, refreshToken);

        String encodedNickname = URLEncoder.encode(member.getNickname(), StandardCharsets.UTF_8);
        response.sendRedirect(frontendUrl + "/oauth-callback"
                + "?token=" + jwtAccessToken
                + "&userId=" + member.getId()
                + "&nickname=" + encodedNickname
                + "&role=" + member.getRole());
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .sameSite("None")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
