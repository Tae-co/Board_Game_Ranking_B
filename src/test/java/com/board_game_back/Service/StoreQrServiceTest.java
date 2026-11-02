package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;

import com.board_game_back.Entity.Store;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 1시간마다 바뀌는 체크인 QR — 지금 칸만 통과, 유예 없음 */
class StoreQrServiceTest {

    private final StoreQrService qr = new StoreQrService();

    private static Store store(Long id, String secret) {
        Store s = Store.builder().name("야다").address("서울").region("South Korea")
            .openTime(LocalTime.of(10, 0)).closeTime(LocalTime.of(22, 0)).qrSecret(secret).build();
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private static String tokenOf(String url) {
        return url.substring(url.indexOf("?t=") + 3);
    }

    @Test
    void 같은_칸이면_같은_토큰이고_통과한다() {
        Store s = store(1L, "secret-a");
        StoreQrService.CurrentQr at05 = qr.currentQr(s, LocalDateTime.of(2026, 11, 2, 5, 5));
        StoreQrService.CurrentQr at55 = qr.currentQr(s, LocalDateTime.of(2026, 11, 2, 5, 55));

        assertThat(at05.url()).isEqualTo(at55.url()).startsWith("https://yadarank.com/s/1?t=");
        assertThat(at05.expiresAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 6, 0));
        assertThat(qr.verify(s, tokenOf(at05.url()), LocalDateTime.of(2026, 11, 2, 5, 59, 59))).isTrue();
    }

    @Test
    void 정각이_지나면_앞_칸_토큰은_거부한다() {
        Store s = store(1L, "secret-a");
        String old = tokenOf(qr.currentQr(s, LocalDateTime.of(2026, 11, 2, 5, 59)).url());

        assertThat(qr.verify(s, old, LocalDateTime.of(2026, 11, 2, 6, 0))).isFalse();
    }

    @Test
    void 다른_매장이나_비밀키가_다르면_거부한다() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 5, 0);
        String token = tokenOf(qr.currentQr(store(1L, "secret-a"), now).url());

        assertThat(qr.verify(store(2L, "secret-a"), token, now)).isFalse();
        assertThat(qr.verify(store(1L, "secret-b"), token, now)).isFalse();
        assertThat(qr.verify(store(1L, "secret-a"), null, now)).isFalse();
    }
}
