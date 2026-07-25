package com.board_game_back.Config;

import java.util.List;

/**
 * HTTP CORS와 WebSocket(STOMP) 핸드셰이크가 공유하는 허용 origin 목록.
 * 두 곳이 어긋나지 않도록 한 곳에서 관리한다 (#19).
 */
public final class AllowedOrigins {

    private AllowedOrigins() {}

    public static final List<String> LIST = List.of(
        "http://localhost:5173",
        "http://localhost:5174",
        "http://localhost:3000",
        "https://boardup.pages.dev",
        "https://yadarank.com",
        "https://www.yadarank.com",
        "https://app.yadarank.com",
        "https://my-boardup.apps.tossmini.com",
        "https://my-boardup.private-apps.tossmini.com",
        "capacitor://localhost",
        "http://localhost",
        "https://localhost"
    );
}
