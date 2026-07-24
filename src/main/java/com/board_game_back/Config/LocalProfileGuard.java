package com.board_game_back.Config;

import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * local 프로필로 뜰 때 운영 자원을 가리키고 있으면 기동을 막는다.
 *
 * <p>local 프로필은 {@code application-local.properties}로 로컬 주소가 고정되지만,
 * 그 파일이 없거나(새 clone) {@code ${ENV}} 플레이스홀더로 되돌아가면
 * {@code .env.local}의 운영 주소가 다시 주입될 수 있다. 프로퍼티 우선순위에만
 * 기대지 않고, <b>해석된 최종 값</b>을 검사해 실수를 기동 시점에 차단한다.
 *
 * <p>{@link ApplicationPreparedEvent}는 Environment가 완전히 채워진 뒤,
 * 빈 생성·DB 연결 전에 발생한다. 여기서 막아야 운영 DB에 손대기 전에 멈춘다.
 *
 * <p>{@code BoardGameBackApplication.main()}에서 명시적으로 등록한다.
 */
public class LocalProfileGuard implements ApplicationListener<ApplicationPreparedEvent> {

    private static final String LOCAL_JWT_SECRET = "local-dev-only-secret-not-valid-in-production";

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();

        // local 프로필이 아니면 검사하지 않는다 (운영 기동에 영향 없음)
        boolean isLocal = false;
        for (String profile : env.getActiveProfiles()) {
            if ("local".equals(profile)) {
                isLocal = true;
                break;
            }
        }
        if (!isLocal) {
            return;
        }

        requireLocal(env, "spring.datasource.url", "DB");
        requireLocal(env, "supabase.url", "스토리지");
        requireLocalJwtSecret(env);
    }

    private void requireLocal(Environment env, String key, String label) {
        String value = env.getProperty(key);
        if (value == null || !(value.contains("127.0.0.1") || value.contains("localhost"))) {
            throw new IllegalStateException(String.format(
                "local 프로필인데 %s(%s)가 로컬을 가리키지 않는다: %s%n"
                + "→ application-local.properties가 127.0.0.1로 고정돼 있는지, "
                + ".env.local의 운영 주소가 새어들어오지 않는지 확인할 것.",
                label, key, value));
        }
    }

    private void requireLocalJwtSecret(Environment env) {
        String secret = env.getProperty("jwt.secret");
        if (!LOCAL_JWT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                "local 프로필인데 jwt.secret이 로컬 전용 값이 아니다.\n"
                + "→ 운영 시크릿으로 서명하면 로컬 토큰이 운영에서도 통한다. "
                + "application-local.properties의 jwt.secret을 확인할 것.");
        }
    }
}
