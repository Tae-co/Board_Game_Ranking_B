package com.board_game_back.Security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // CSRF 미사용
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").permitAll() // /api로 시작하는 모든 경로는 인증 없이 허용
                .anyRequest().permitAll() // 우선 개발 중에는 모든 접근 허용
            );
        return http.build();
    }
}
