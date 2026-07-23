package com.board_game_back.Security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * 보안점검 #4 회귀 테스트.
 *
 * <p>정상 토큰 통과는 Apple 개인키가 있어야 만들 수 있어 여기서 검증할 수 없다.
 * 실제 기기에서 Apple 로그인으로 확인해야 한다.
 */
class AppleTokenVerifierTest {

    private final AppleTokenVerifier verifier = new AppleTokenVerifier("com.taeco.YadaRank");

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 서명없이_payload만_위조한_토큰은_거부된다() {
        // 이전 코드는 parts[1]만 Base64 디코드해 sub를 신뢰했다.
        // {"sub":"아무값"} 을 만들어 보내면 임의 Apple 사용자로 로그인됐다.
        String forged = "x." + b64("{\"sub\":\"001234.deadbeef\"}") + ".x";

        assertThatThrownBy(() -> verifier.verifyAndGetSub(forged))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 서명알고리즘이_none인_토큰은_거부된다() {
        String token = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}")
                + "." + b64("{\"iss\":\"https://appleid.apple.com\","
                + "\"aud\":\"com.taeco.YadaRank\",\"sub\":\"001234.deadbeef\"}")
                + ".";

        assertThatThrownBy(() -> verifier.verifyAndGetSub(token))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kid가_없으면_거부된다() {
        // iss·aud를 진짜처럼 채워도 어느 공개키로 검증할지 모르면 통과시키지 않는다
        String token = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}")
                + "." + b64("{\"iss\":\"https://appleid.apple.com\","
                + "\"aud\":\"com.taeco.YadaRank\",\"sub\":\"001234.deadbeef\"}")
                + ".c2lnbmF0dXJl";

        assertThatThrownBy(() -> verifier.verifyAndGetSub(token))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void JWT_형식이_아니면_거부된다() {
        assertThatThrownBy(() -> verifier.verifyAndGetSub("not-a-jwt"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
