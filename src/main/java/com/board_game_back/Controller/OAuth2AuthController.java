package com.board_game_back.Controller;

import com.board_game_back.Entity.Member;
import com.board_game_back.Security.JwtTokenProvider;
import com.board_game_back.Service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class OAuth2AuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthController.class);

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${GOOGLE_CLIENT_ID:}")
    private String googleClientId;

    @Value("${GOOGLE_CLIENT_SECRET:}")
    private String googleClientSecret;

    @Value("${KAKAO_REST_API_KEY:}")
    private String kakaoRestApiKey;

    @Value("${KAKAO_CLIENT_SECRET:}")
    private String kakaoClientSecret;

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

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USER_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private static final String KAKAO_AUTH_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USER_URL = "https://kapi.kakao.com/v2/user/me";

    /** 구글 인증 페이지로 리다이렉트 */
    @GetMapping("/google")
    public void googleLogin(HttpServletResponse response,
                            @RequestParam(required = false) String returnTo) throws IOException {
        String redirectUri = backendUrl + "/api/auth/google/callback";
        String url = GOOGLE_AUTH_URL
                + "?client_id=" + googleClientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=profile+email"
                + "&prompt=select_account"
                + "&state=" + URLEncoder.encode(encodeState(returnTo), StandardCharsets.UTF_8);
        response.sendRedirect(url);
    }

    /** 구글 OAuth2 콜백 (웹) */
    @GetMapping("/google/callback")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void googleCallback(@RequestParam String code,
                               @RequestParam(required = false) String state,
                               HttpServletResponse response) throws IOException {
        try {
            Map<String, Object> userBody = fetchGoogleUser(code, backendUrl + "/api/auth/google/callback");
            String socialId = "GOOGLE_" + userBody.get("sub");
            String nickname = (String) userBody.getOrDefault("name", "구글유저");
            redirectWithToken(response, socialId, nickname, false, resolveFrontendUrl(state));
        } catch (Exception e) {
            logOAuthError("google", e);
            response.sendRedirect(frontendUrl + "/login?error=google");
        }
    }

    /** 네이티브 앱용 구글 로그인 */
    @GetMapping("/google/native/login")
    public void googleNativeLogin(HttpServletResponse response) throws IOException {
        String redirectUri = backendUrl + "/api/auth/google/native/callback";
        String url = GOOGLE_AUTH_URL
                + "?client_id=" + googleClientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=profile+email"
                + "&prompt=select_account";
        response.sendRedirect(url);
    }

    /** 네이티브 앱용 구글 OAuth2 콜백 → yadarank:// 딥링크로 리다이렉트 */
    @GetMapping("/google/native/callback")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void googleNativeCallback(@RequestParam String code, HttpServletResponse response) throws IOException {
        try {
            Map<String, Object> userBody = fetchGoogleUser(code, backendUrl + "/api/auth/google/native/callback");
            String socialId = "GOOGLE_" + userBody.get("sub");
            String nickname = (String) userBody.getOrDefault("name", "구글유저");
            redirectWithToken(response, socialId, nickname, true);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            response.sendRedirect(frontendUrl + "/login?error=google&msg=" + URLEncoder.encode(msg, StandardCharsets.UTF_8));
        }
    }


    /** 웹용 카카오 로그인 */
    @GetMapping("/kakao/login")
    public void kakaoLogin(HttpServletResponse response,
                           @RequestParam(required = false) String returnTo) throws IOException {
        String redirectUri = backendUrl + "/api/auth/kakao/callback";
        String url = KAKAO_AUTH_URL
                + "?client_id=" + kakaoRestApiKey
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&state=" + URLEncoder.encode(encodeState(returnTo), StandardCharsets.UTF_8);
        response.sendRedirect(url);
    }

    /** 네이티브 앱용 카카오 로그인 → 항상 yadarank:// 딥링크로 리다이렉트 */
    @GetMapping("/kakao/native/login")
    public void kakaoNativeLogin(HttpServletResponse response) throws IOException {
        String redirectUri = backendUrl + "/api/auth/kakao/native/callback";
        String url = KAKAO_AUTH_URL
                + "?client_id=" + kakaoRestApiKey
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code";
        response.sendRedirect(url);
    }

    /** 웹용 카카오 OAuth2 콜백 */
    @GetMapping("/kakao/callback")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void kakaoCallback(@RequestParam String code,
                              @RequestParam(required = false) String state,
                              HttpServletResponse response) throws IOException {
        try {
            Map<String, Object> userBody = fetchKakaoUser(code, backendUrl + "/api/auth/kakao/callback");
            String socialId = "kakao_" + userBody.get("id");
            String nickname = extractKakaoNickname(userBody);
            redirectWithToken(response, socialId, nickname, false, resolveFrontendUrl(state));
        } catch (Exception e) {
            logOAuthError("kakao", e);
            response.sendRedirect(frontendUrl + "/login?error=kakao");
        }
    }

    /** 네이티브 앱용 카카오 OAuth2 콜백 → yadarank:// 딥링크로 리다이렉트 */
    @GetMapping("/kakao/native/callback")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void kakaoNativeCallback(@RequestParam String code, HttpServletResponse response) throws IOException {
        try {
            Map<String, Object> userBody = fetchKakaoUser(code, backendUrl + "/api/auth/kakao/native/callback");
            String socialId = "kakao_" + userBody.get("id");
            String nickname = extractKakaoNickname(userBody);
            redirectWithToken(response, socialId, nickname, true);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            response.sendRedirect(frontendUrl + "/login?error=kakao&msg=" + URLEncoder.encode(msg, StandardCharsets.UTF_8));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> fetchGoogleUser(String code, String redirectUri) {
        RestTemplate restTemplate = new RestTemplate();
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        Map tokenBody = restTemplate.postForEntity(GOOGLE_TOKEN_URL, new HttpEntity<>(params, headers), Map.class).getBody();
        String accessToken = (String) tokenBody.get("access_token");
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(accessToken);
        return restTemplate.exchange(GOOGLE_USER_URL, HttpMethod.GET, new HttpEntity<>(userHeaders), Map.class).getBody();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> fetchKakaoUser(String code, String redirectUri) {
        RestTemplate restTemplate = new RestTemplate();
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoRestApiKey);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);
        if (kakaoClientSecret != null && !kakaoClientSecret.isBlank()) {
            params.add("client_secret", kakaoClientSecret);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        Map tokenBody = restTemplate.postForEntity(KAKAO_TOKEN_URL, new HttpEntity<>(params, headers), Map.class).getBody();
        String accessToken = (String) tokenBody.get("access_token");
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(accessToken);
        return restTemplate.exchange(KAKAO_USER_URL, HttpMethod.GET, new HttpEntity<>(userHeaders), Map.class).getBody();
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

    @SuppressWarnings("unchecked")
    private String extractKakaoNickname(Map<String, Object> userBody) {
        Map<String, Object> kakaoAccount = (Map<String, Object>) userBody.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
        return (String) profile.get("nickname");
    }

    private void redirectWithToken(HttpServletResponse response, String socialId, String nickname, boolean isNative) throws IOException {
        redirectWithToken(response, socialId, nickname, isNative, frontendUrl);
    }

    private void redirectWithToken(HttpServletResponse response, String socialId, String nickname, boolean isNative, String targetFrontendUrl) throws IOException {
        Member member = authService.findOrCreateOAuthMember(socialId, nickname);

        String jwtAccessToken = jwtTokenProvider.generateAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getId());

        String encodedNickname = URLEncoder.encode(member.getNickname(), StandardCharsets.UTF_8);
        String queryParams = "?token=" + jwtAccessToken
                + "&userId=" + member.getId()
                + "&nickname=" + encodedNickname
                + "&role=" + member.getRole()
                + "&refreshToken=" + refreshToken;

        if (isNative) {
            response.sendRedirect("yadarank://oauth-callback" + queryParams);
        } else {
            response.sendRedirect(targetFrontendUrl + "/oauth-callback" + queryParams);
        }
    }
}
