package com.board_game_back.Security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Apple identityToken 검증.
 *
 * <p>identityToken은 클라이언트가 보내는 값이라 그대로 믿으면 안 된다.
 * Apple 공개키로 서명을 확인해야 위조 토큰을 걸러낼 수 있다.
 */
@Component
public class AppleTokenVerifier {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";

    /** kid → 공개키. Apple이 키를 주기적으로 교체하므로 모르는 kid를 만나면 다시 받아온다. */
    private final Map<String, Key> keyCache = new ConcurrentHashMap<>();
    private final String clientId;

    public AppleTokenVerifier(@Value("${apple.client-id}") String clientId) {
        this.clientId = clientId;
    }

    /**
     * 서명·iss·aud·exp를 검증하고 sub(Apple 사용자 고유 ID)를 돌려준다.
     * 검증에 실패하면 IllegalArgumentException (AuthController가 401로 변환).
     */
    public String verifyAndGetSub(String identityToken) {
        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                        @Override
                        @SuppressWarnings("rawtypes")
                        public Key resolveSigningKey(JwsHeader header, Claims claims) {
                            return resolveKey(header.getKeyId());
                        }
                    })
                    .requireIssuer(APPLE_ISSUER)
                    .requireAudience(clientId)
                    .build()
                    .parseClaimsJws(identityToken)
                    .getBody();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("유효하지 않은 Apple 토큰입니다.");
        }

        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("유효하지 않은 Apple 토큰입니다.");
        }
        return sub;
    }

    /** kid에 해당하는 공개키. 캐시에 없으면 Apple에서 목록을 다시 받아온다. */
    private Key resolveKey(String kid) {
        if (kid == null || kid.isBlank()) {
            throw new IllegalArgumentException("유효하지 않은 Apple 토큰입니다.");
        }
        Key cached = keyCache.get(kid);
        if (cached != null) return cached;

        refreshKeys();

        Key key = keyCache.get(kid);
        if (key == null) {
            throw new IllegalArgumentException("유효하지 않은 Apple 토큰입니다.");
        }
        return key;
    }

    private void refreshKeys() {
        try {
            String body = new RestTemplate().getForObject(APPLE_JWKS_URL, String.class);
            JsonNode keys = new ObjectMapper().readTree(body).get("keys");
            for (JsonNode jwk : keys) {
                if (!"RSA".equals(jwk.path("kty").asText())) continue;
                keyCache.put(jwk.path("kid").asText(), toPublicKey(jwk));
            }
        } catch (Exception e) {
            // 검증 못 하면 통과시키지 않는다
            throw new IllegalArgumentException("Apple 공개키를 가져오지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private Key toPublicKey(JsonNode jwk) throws Exception {
        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("n").asText()));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("e").asText()));
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }
}
