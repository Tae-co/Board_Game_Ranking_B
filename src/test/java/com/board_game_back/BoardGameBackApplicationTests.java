package com.board_game_back;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// jwt.secret의 운영 기본값을 없앤 뒤(#5), 테스트 컨텍스트가 환경변수/.env.local에
// 의존하지 않도록 테스트 전용 시크릿을 주입한다.
@SpringBootTest(properties = "jwt.secret=test-only-secret-not-for-production")
class BoardGameBackApplicationTests {

    @Test
    void contextLoads() {
    }

}
