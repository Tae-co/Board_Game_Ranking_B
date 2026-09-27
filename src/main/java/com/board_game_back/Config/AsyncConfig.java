package com.board_game_back.Config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    /**
     * 계측 전용 실행기.
     *
     * <p>기본 executor는 코어 8스레드 + 무한 큐라, 이벤트 인서트 8개가 Hikari 커넥션 5개
     * (spring.datasource.hikari.maximum-pool-size)를 실사용 요청과 경쟁해서 가져간다.
     * 요청 스레드를 분리해도 커넥션 풀은 공유하므로, 계측 버스트가 실제 사용자 요청을
     * connection-timeout(20초)까지 대기시킬 수 있다. 게다가 /api/events는 permitAll이다.
     *
     * <p>스레드를 최대 2개로 묶어 최악의 경우에도 커넥션 3개는 제품 몫으로 남긴다.
     */
    @Bean("eventExecutor")
    public Executor eventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("event-");
        // 큐가 차면 조용히 버린다. CallerRunsPolicy는 계측을 요청 스레드로 되돌려
        // "사용자 흐름에 영향이 없어야 한다"는 전제를 깬다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }
}
