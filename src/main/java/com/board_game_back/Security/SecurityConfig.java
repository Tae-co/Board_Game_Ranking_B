package com.board_game_back.Security;

import java.util.List;
import org.springframework.http.HttpMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(com.board_game_back.Config.AllowedOrigins.LIST);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 공개 엔드포인트만 명시적으로 허용 (로그인 플로우·헬스·이미지·게임 목록).
                // 과거의 GET /** permitAll은 방/커뮤니티 초대코드 노출 + admin GET 우회를 유발해 제거.
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/oauth2/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/api/health").permitAll()
                // 에러가 나면 서블릿이 /error로 넘긴다. 여기가 막혀 있으면 403(관리자 아님)·400(필수 값 누락)이
                // 전부 401로 바뀌어, 앱이 로그인 만료로 오해한다. 상태 코드만 그대로 내보내는 경로라 열어 둔다.
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/images/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/games/**").permitAll()
                // 행동 로그: 로그인 전 초대 랜딩 유입까지 측정해야 해서 비로그인도 허용한다.
                // event_name은 EventName enum 화이트리스트다. 화이트리스트 밖 이름과
                // 서버 전용 이벤트(MEMBER_DELETED)는 202로 받되 저장하지 않는다 —
                // 구버전 앱이 폐기된 이름을 보내는 건 정상 상황이라 400으로 만들면
                // 500 에러 로그만 쌓인다. 판정은 UserEventService가 한다.
                .requestMatchers(HttpMethod.POST, "/api/events").permitAll()
                // 관리자 전용
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/matches/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/seasons/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e.authenticationEntryPoint(
                (req, res, ex) -> res.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
            ))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
