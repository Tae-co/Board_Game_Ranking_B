package com.board_game_back.Utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 메모리 안의 간단한 요청 제한 (키마다 최근 window 안 요청 수). 서버가 한 대라 이걸로 충분하다.
 * 매장 기기 등록 숫자 맞히기(6자리)를 막는 용도 (plan-store-impl §3.2).
 */
public class SlidingWindowLimiter {

    private static final int MAX_KEYS = 10_000;

    private final int limit;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(int limit, long windowMillis) {
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    /** 허용이면 true, 넘쳤으면 false */
    public boolean tryAcquire(String key, long nowMillis) {
        if (hits.size() > MAX_KEYS) hits.clear();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && nowMillis - q.peekFirst() >= windowMillis) q.pollFirst();
            if (q.size() >= limit) return false;
            q.addLast(nowMillis);
            return true;
        }
    }
}
