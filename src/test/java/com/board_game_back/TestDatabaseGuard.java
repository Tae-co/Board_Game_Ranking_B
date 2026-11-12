package com.board_game_back;

import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * 테스트에서 Spring 컨텍스트가 뜰 때 DB·스토리지가 로컬이 아니면 연결 전에 멈춘다.
 *
 * <p>{@code application.properties}가 {@code .env.local}을 불러오는데, 거기엔 운영 Supabase 주소가 있다.
 * 테스트는 local 프로필이 아니라 {@code LocalProfileGuard}를 지나치므로 운영 DB에 붙을 수 있었다 (2026-11-10).
 * {@code build.gradle}의 test 태스크가 로컬 주소를 고정하지만, IDE 실행처럼 그 설정이 빠지는 경우를 여기서 막는다.
 *
 * <p>{@code src/test/resources/META-INF/spring.factories}로 등록한다 — 테스트 클래스패스에만 있어 운영 앱엔 없다.
 */
public class TestDatabaseGuard implements ApplicationListener<ApplicationPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        requireLocal(env, "spring.datasource.url");
        requireLocal(env, "supabase.url");
    }

    private void requireLocal(Environment env, String key) {
        String value = env.getProperty(key);
        if (value == null || !(value.contains("127.0.0.1") || value.contains("localhost"))) {
            throw new IllegalStateException(String.format(
                "테스트인데 %s가 로컬을 가리키지 않는다: %s%n"
                + "→ 운영 DB에 붙을 수 있어 멈췄다. ./gradlew test로 실행하거나(build.gradle이 로컬로 고정), "
                + "IDE라면 -D%s=로컬 주소를 줄 것.",
                key, value, key));
        }
    }
}
