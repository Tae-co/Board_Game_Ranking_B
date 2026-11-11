package com.board_game_back.Controller;

import com.board_game_back.Entity.Member;
import com.board_game_back.Security.JwtTokenProvider;
import com.board_game_back.Service.AuthService;
import com.board_game_back.Service.OAuthCodeStore;
import com.board_game_back.Service.OAuthProviderClient;
import com.board_game_back.Service.OAuthProviderClient.OAuthUser;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class OAuth2AuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthController.class);

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final OAuthCodeStore oauthCodeStore;
    private final OAuthProviderClient oauthClient;

    @Value("${FRONTEND_URL:https://boardup.pages.dev}")
    private String frontendUrl;

    @Value("${BACKEND_URL:https://meeple-production.up.railway.app}")
    private String backendUrl;

    private static final Set<String> ALLOWED_ORIGINS = Set.of(
        "http://localhost:5173",
        "http://localhost:5174",
        "https://boardup.pages.dev",
        "https://yadarank.com",
        "https://www.yadarank.com",
        "https://app.yadarank.com"
    );

    private String encodeState(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) return "";
        return Base64.getUrlEncoder().encodeToString(returnTo.getBytes(StandardCharsets.UTF_8));
    }

    private String resolveFrontendUrl(String state) {
        if (state != null && !state.isBlank()) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
                if (ALLOWED_ORIGINS.contains(decoded)) return decoded;
            } catch (Exception ignored) {}
        }
        return frontendUrl;
    }

    /** 구글 인증 페이지로 리다이렉트 */
    @GetMapping("/google")
    public void googleLogin(HttpServletResponse response,
                            @RequestParam(required = false) String returnTo) throws IOException {
        response.sendRedirect(oauthClient.googleAuthorizeUrl(
                backendUrl + "/api/auth/google/callback", encodeState(returnTo)));
    }

    /** 구글 OAuth2 콜백 (웹) */
    @GetMapping("/google/callback")
    public void googleCallback(@RequestParam String code,
                               @RequestParam(required = false) String state,
                               HttpServletResponse response) throws IOException {
        try {
            OAuthUser user = oauthClient.fetchGoogleUser(code, backendUrl + "/api/auth/google/callback");
            redirectWithToken(response, user, false, resolveFrontendUrl(state));
        } catch (Exception e) {
            logOAuthError("google", e);
            response.sendRedirect(frontendUrl + "/login?error=google");
        }
    }

    /** 네이티브 앱용 구글 로그인 */
    @GetMapping("/google/native/login")
    public void googleNativeLogin(HttpServletResponse response) throws IOException {
        response.sendRedirect(oauthClient.googleAuthorizeUrl(
                backendUrl + "/api/auth/google/native/callback", null));
    }

    /** 네이티브 앱용 구글 OAuth2 콜백 → yadarank:// 딥링크로 리다이렉트 */
    @GetMapping("/google/native/callback")
    public void googleNativeCallback(@RequestParam String code, HttpServletResponse response) throws IOException {
        try {
            OAuthUser user = oauthClient.fetchGoogleUser(code, backendUrl + "/api/auth/google/native/callback");
            redirectWithToken(response, user, true, frontendUrl);
        } catch (Exception e) {
            redirectNativeError(response, "google", e);
        }
    }

    /** 웹용 카카오 로그인 */
    @GetMapping("/kakao/login")
    public void kakaoLogin(HttpServletResponse response,
                           @RequestParam(required = false) String returnTo) throws IOException {
        response.sendRedirect(oauthClient.kakaoAuthorizeUrl(
                backendUrl + "/api/auth/kakao/callback", encodeState(returnTo)));
    }

    /** 네이티브 앱용 카카오 로그인 → 항상 yadarank:// 딥링크로 리다이렉트 */
    @GetMapping("/kakao/native/login")
    public void kakaoNativeLogin(HttpServletResponse response) throws IOException {
        response.sendRedirect(oauthClient.kakaoAuthorizeUrl(
                backendUrl + "/api/auth/kakao/native/callback", null));
    }

    /** 웹용 카카오 OAuth2 콜백 */
    @GetMapping("/kakao/callback")
    public void kakaoCallback(@RequestParam String code,
                              @RequestParam(required = false) String state,
                              HttpServletResponse response) throws IOException {
        try {
            OAuthUser user = oauthClient.fetchKakaoUser(code, backendUrl + "/api/auth/kakao/callback");
            redirectWithToken(response, user, false, resolveFrontendUrl(state));
        } catch (Exception e) {
            logOAuthError("kakao", e);
            response.sendRedirect(frontendUrl + "/login?error=kakao");
        }
    }

    /** 네이티브 앱용 카카오 OAuth2 콜백 → yadarank:// 딥링크로 리다이렉트 */
    @GetMapping("/kakao/native/callback")
    public void kakaoNativeCallback(@RequestParam String code, HttpServletResponse response) throws IOException {
        try {
            OAuthUser user = oauthClient.fetchKakaoUser(code, backendUrl + "/api/auth/kakao/native/callback");
            redirectWithToken(response, user, true, frontendUrl);
        } catch (Exception e) {
            redirectNativeError(response, "kakao", e);
        }
    }

    private void redirectNativeError(HttpServletResponse response, String provider, Exception e) throws IOException {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        response.sendRedirect(frontendUrl + "/login?error=" + provider + "&msg=" + URLEncoder.encode(msg, StandardCharsets.UTF_8));
    }

    /** OAuth 콜백 실패 원인을 로그로 남긴다. 토큰/사용자정보 요청 실패면 공급자가 준 응답 본문까지 찍는다. */
    private void logOAuthError(String provider, Exception e) {
        if (e instanceof HttpStatusCodeException httpEx) {
            log.error("[OAuth:{}] 콜백 실패 status={} body={}",
                    provider, httpEx.getStatusCode(), httpEx.getResponseBodyAsString(), e);
        } else {
            log.error("[OAuth:{}] 콜백 실패: {}", provider, e.toString(), e);
        }
    }

    private void redirectWithToken(HttpServletResponse response, OAuthUser user, boolean isNative, String targetFrontendUrl) throws IOException {
        Member member = authService.findOrCreateOAuthMember(user.socialId(), user.nickname());

        String jwtAccessToken = jwtTokenProvider.generateAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getId());

        if (isNative) {
            // 네이티브는 기존 방식(토큰 URL) 유지 — 이미 배포된 구버전 앱과의 호환을 위해서다.
            // 딥링크 토큰은 서버 로그에 남지 않고(웹과 달리), 로컬 앱 가로채기(#13)는 별도로 skip한 항목.
            // #12 네이티브 전환은 신버전 앱 확산 후 별도로 진행한다.
            String encodedNickname = URLEncoder.encode(member.getNickname(), StandardCharsets.UTF_8);
            String queryParams = "?token=" + jwtAccessToken
                    + "&userId=" + member.getId()
                    + "&nickname=" + encodedNickname
                    + "&role=" + member.getRole()
                    + "&refreshToken=" + refreshToken;
            response.sendRedirect("yadarank://oauth-callback" + queryParams);
        } else {
            // 웹은 토큰을 URL에 싣지 않는다(#12). 1회용 code만 넘기고, 프론트가 교환해 토큰을 받는다.
            String code = oauthCodeStore.issue(new OAuthCodeStore.TokenBundle(
                    jwtAccessToken, refreshToken, member.getId(), member.getNickname(), member.getRole()));
            response.sendRedirect(targetFrontendUrl + "/oauth-callback?code=" + code);
        }
    }

    public record OAuthExchangeRequest(String code) {}

    /** OAuth 1회용 code를 토큰으로 교환한다(#12). 로그인 직후라 인증 없이 접근 가능(/api/auth/**). */
    @PostMapping("/oauth/exchange")
    public ResponseEntity<?> exchangeCode(@RequestBody(required = false) OAuthExchangeRequest request) {
        String code = request != null ? request.code() : null;
        OAuthCodeStore.TokenBundle bundle = oauthCodeStore.consume(code);
        if (bundle == null) {
            return ResponseEntity.status(401).body("유효하지 않거나 만료된 코드입니다.");
        }
        return ResponseEntity.ok(bundle);
    }
}
