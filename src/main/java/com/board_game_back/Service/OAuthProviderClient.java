package com.board_game_back.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/** 구글·카카오 OAuth 서버와 주고받는 부분 (인증 URL 만들기, code → 사용자 정보). */
@Component
public class OAuthProviderClient {

    /** 공급자에서 받아 온 사용자. socialId는 공급자 접두어까지 붙은 값이다 (GOOGLE_xxx, kakao_xxx). */
    public record OAuthUser(String socialId, String nickname) {}

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USER_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private static final String KAKAO_AUTH_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USER_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${GOOGLE_CLIENT_ID:}")
    private String googleClientId;

    @Value("${GOOGLE_CLIENT_SECRET:}")
    private String googleClientSecret;

    @Value("${KAKAO_REST_API_KEY:}")
    private String kakaoRestApiKey;

    @Value("${KAKAO_CLIENT_SECRET:}")
    private String kakaoClientSecret;

    /** state가 null이면 state 파라미터를 붙이지 않는다 (네이티브 로그인). */
    public String googleAuthorizeUrl(String redirectUri, String state) {
        return GOOGLE_AUTH_URL
                + "?client_id=" + googleClientId
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=profile+email"
                + "&prompt=select_account"
                + (state != null ? "&state=" + encode(state) : "");
    }

    public String kakaoAuthorizeUrl(String redirectUri, String state) {
        return KAKAO_AUTH_URL
                + "?client_id=" + kakaoRestApiKey
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + (state != null ? "&state=" + encode(state) : "");
    }

    public OAuthUser fetchGoogleUser(String code, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);

        Map<String, Object> body = fetchUser(GOOGLE_TOKEN_URL, GOOGLE_USER_URL, params);
        String nickname = (String) body.getOrDefault("name", "구글유저");
        return new OAuthUser("GOOGLE_" + body.get("sub"), nickname);
    }

    @SuppressWarnings("unchecked")
    public OAuthUser fetchKakaoUser(String code, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoRestApiKey);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);
        if (kakaoClientSecret != null && !kakaoClientSecret.isBlank()) {
            params.add("client_secret", kakaoClientSecret);
        }

        Map<String, Object> body = fetchUser(KAKAO_TOKEN_URL, KAKAO_USER_URL, params);
        Map<String, Object> kakaoAccount = (Map<String, Object>) body.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
        return new OAuthUser("kakao_" + body.get("id"), (String) profile.get("nickname"));
    }

    /** code로 access token을 받고, 그 토큰으로 사용자 정보를 가져온다. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> fetchUser(String tokenUrl, String userUrl, MultiValueMap<String, String> params) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        Map tokenBody = restTemplate.postForEntity(tokenUrl, new HttpEntity<>(params, headers), Map.class).getBody();
        String accessToken = (String) tokenBody.get("access_token");

        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(accessToken);
        return restTemplate.exchange(userUrl, HttpMethod.GET, new HttpEntity<>(userHeaders), Map.class).getBody();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
