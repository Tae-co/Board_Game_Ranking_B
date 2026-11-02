package com.board_game_back.Service;

import com.board_game_back.Entity.Store;
import com.board_game_back.Utils.StoreClock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * 1시간마다 바뀌는 체크인 QR (plan-store §6.6).
 *
 * <p>토큰 = HMAC(매장 비밀키, "매장id:정각 칸 시작"). 매장 지역 시간의 정각 칸이라 그 매장의 모든 기기가 같은 QR을 띄운다.
 * 확인은 <b>지금 칸</b>만 맞는지 본다 — 유예 없음 (29차).
 */
@Service
public class StoreQrService {

    public static final String QR_BASE_URL = "https://yadarank.com/s/";

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public record CurrentQr(String url, LocalDateTime expiresAt, LocalDateTime serverNow) {}

    public CurrentQr currentQr(Store store, LocalDateTime utcNow) {
        LocalDateTime slot = StoreClock.hourSlotStart(store, utcNow);
        String url = QR_BASE_URL + store.getId() + "?t=" + token(store, slot);
        return new CurrentQr(url, slot.plusHours(1), utcNow);
    }

    public boolean verify(Store store, String token, LocalDateTime utcNow) {
        if (token == null) return false;
        String expected = token(store, StoreClock.hourSlotStart(store, utcNow));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    String token(Store store, LocalDateTime slotStart) {
        long epoch = slotStart.toEpochSecond(ZoneOffset.UTC);
        byte[] mac = hmac(store.getQrSecret(), store.getId() + ":" + epoch);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(mac, 16));
    }

    static byte[] hmac(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 계산 실패", e);
        }
    }
}
