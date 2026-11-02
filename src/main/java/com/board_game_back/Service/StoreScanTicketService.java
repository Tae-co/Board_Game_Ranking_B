package com.board_game_back.Service;

import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * QR을 찍은 순간을 증명하는 티켓. QR은 찍은 순간 한 번만 확인하고, 그 뒤 로그인·가입 동의 중에
 * QR이 바뀌어도 그대로 가입·체크인한다 (plan-store 30차). 로그인 전에 발급하므로 서버에 저장하지 않는다.
 *
 * <p>형식: {@code 매장id.찍은시각(epoch초).서명}. 서명 키는 {@code jwt.secret}에서 용도 접두어로 유도한다.
 */
@Service
public class StoreScanTicketService {

    /** 티켓으로 가입·체크인할 수 있는 기간. 체크인 시각은 찍은 순간이라 3시간이 넘으면 가입만 된다 */
    public static final Duration TICKET_VALIDITY = Duration.ofHours(24);

    private final String key;

    public StoreScanTicketService(@Value("${jwt.secret}") String jwtSecret) {
        this.key = "store-scan:" + jwtSecret;
    }

    public String issue(Long storeId, LocalDateTime scannedAt) {
        String payload = storeId + "." + scannedAt.toEpochSecond(ZoneOffset.UTC);
        return payload + "." + sign(payload);
    }

    /** @return 찍은 시각 (UTC) */
    public LocalDateTime verify(String ticket, Long storeId, LocalDateTime utcNow) {
        if (ticket == null) throw new StoreException(StoreErrorCode.TICKET_INVALID);
        String[] parts = ticket.split("\\.");
        if (parts.length != 3) throw new StoreException(StoreErrorCode.TICKET_INVALID);
        String payload = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new StoreException(StoreErrorCode.TICKET_INVALID);
        }
        if (!parts[0].equals(String.valueOf(storeId))) throw new StoreException(StoreErrorCode.TICKET_INVALID);

        LocalDateTime scannedAt;
        try {
            scannedAt = LocalDateTime.ofEpochSecond(Long.parseLong(parts[1]), 0, ZoneOffset.UTC);
        } catch (NumberFormatException e) {
            throw new StoreException(StoreErrorCode.TICKET_INVALID);
        }
        if (!utcNow.isBefore(scannedAt.plus(TICKET_VALIDITY))) throw new StoreException(StoreErrorCode.TICKET_EXPIRED);
        return scannedAt;
    }

    private String sign(String payload) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(StoreQrService.hmac(key, payload));
    }
}
