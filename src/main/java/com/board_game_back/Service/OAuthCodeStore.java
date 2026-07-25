package com.board_game_back.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * OAuth 콜백에서 발급한 토큰을 URL 쿼리에 싣지 않기 위해(#12), 1회용 코드에
 * 매핑해 짧게 보관한다. 프론트가 code를 POST로 교환해 토큰을 받는다.
 *
 * - 단일 사용: consume 시 제거되어 재사용 불가.
 * - 짧은 TTL(2분): 만료 코드는 교환 실패.
 * - thread-safe(ConcurrentHashMap) + issue 시 만료분 청소로 무한 증가 방지.
 *   (#10 OTP HashMap의 만료·동시성 문제를 반복하지 않는다.)
 */
@Component
public class OAuthCodeStore {

    private static final long TTL_MILLIS = 120_000; // 2분

    public record TokenBundle(String token, String refreshToken,
                              Long userId, String nickname, String role) {}

    private record Entry(TokenBundle bundle, long expiresAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** 토큰 묶음을 저장하고 1회용 코드를 반환한다. */
    public String issue(TokenBundle bundle) {
        purgeExpired();
        String code = UUID.randomUUID().toString();
        store.put(code, new Entry(bundle, System.currentTimeMillis() + TTL_MILLIS));
        return code;
    }

    /** 코드를 소비(제거)하고 유효하면 토큰 묶음을, 아니면 null을 반환한다. */
    public TokenBundle consume(String code) {
        if (code == null) return null;
        Entry entry = store.remove(code);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()) return null;
        return entry.bundle();
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        store.values().removeIf(e -> e.expiresAt() < now);
    }
}
