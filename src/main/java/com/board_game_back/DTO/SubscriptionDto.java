package com.board_game_back.DTO;

import java.time.LocalDateTime;

public class SubscriptionDto {

    /** 구독 시작 요청. 결제 정보는 없다 — 프로토타입이라 플랜만 받는다. */
    public record SubscribeRequest(String billing) {}

    /**
     * 구독 상태. active가 판정 결과고 나머지는 표시용이다.
     * canceled여도 proUntil 전까지는 active가 true다 (낸 기간은 쓴다).
     */
    public record Response(
        boolean active,
        String billing,
        LocalDateTime proUntil,
        boolean canceled
    ) {
        public static Response free() {
            return new Response(false, null, null, false);
        }
    }
}
