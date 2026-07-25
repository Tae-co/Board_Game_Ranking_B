package com.board_game_back.Utils;

import java.util.UUID;
import java.util.function.Predicate;

public class InviteCodeUtil {

    private static final int MAX_ATTEMPTS = 10;

    private InviteCodeUtil() {}

    public static String generate() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /**
     * 이미 사용 중인 코드는 건너뛰고 미사용 코드를 반환한다 (#20).
     * exists가 true면 재시도하며, 한도를 넘으면 예외로 실패시킨다(무한루프 방지).
     */
    public static String generateUnique(Predicate<String> exists) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String code = generate();
            if (!exists.test(code)) return code;
        }
        throw new IllegalStateException("초대 코드 생성 실패: 충돌 재시도 " + MAX_ATTEMPTS + "회 초과");
    }
}
