package com.board_game_back;

import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        // admin 계정이 없으면 자동 생성
        if (memberRepository.findByUsername("admin").isEmpty()) {
            Member admin = Member.builder()
                    .username("admin")
                    .nickname("관리자")
                    .password(passwordEncoder.encode("admin1234"))
                    .role("ADMIN")
                    .build();
            memberRepository.save(admin);
            System.out.println("[DataInitializer] 관리자 계정이 생성되었습니다. (username: admin, password: admin1234)");
        }
    }
}
