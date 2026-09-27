package com.board_game_back.DTO;

import java.util.Map;

public class EventDto {

    /**
     * eventName을 enum이 아니라 String으로 받는다. enum으로 받으면 알 수 없는 이름이
     * 역직렬화 단계에서 예외가 되어 500이 나간다 — 구버전 앱이 폐기된 이벤트를 보내는
     * 정상적인 상황에서 서버 에러가 쌓인다. 화이트리스트 판정은 서비스에서 하고 조용히 버린다.
     */
    public record LogRequest(
            String eventName,
            String anonId,
            Long communityId,
            Long roomId,
            Long boardGameId,
            Map<String, Object> props,
            String sessionId,
            String platform,
            String appVersion) {}
}
