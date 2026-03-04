package com.board_game_back.Service;

import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.MemberRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;

    // 테스트용 메모리 저장소 (전화번호 : 인증번호) - 실제로는 Redis 추천
    private final Map<String, String> otpStorage = new HashMap<>();

    /**
     * 1. 인증번호 발송 (가상)
     */
    public void sendOtp(String phoneNumber) {
        // 6자리 랜덤 번호 생성
        String otpCode = String.format("%06d", new Random().nextInt(1000000));
        otpStorage.put(phoneNumber, otpCode);

        // 콘솔에 출력 (실제 SMS 대신 여기서 확인!)
        System.out.println("전화번호 [" + phoneNumber + "] 인증번호: " + otpCode);
    }

    /**
     * 2. 인증번호 확인 및 로그인(회원가입)
     */
    @Transactional
    public Member login(String phoneNumber, String otpCode) {
        // 인증번호 확인
        String savedCode = otpStorage.get(phoneNumber);
        if (savedCode == null || !savedCode.equals(otpCode)) {
            throw new IllegalArgumentException("인증번호가 일치하지 않거나 만료되었습니다.");
        }

        // 인증 성공 시 메모리에서 삭제
        otpStorage.remove(phoneNumber);

        // 기존 회원인지 확인, 없으면 새로 가입 (닉네임은 기본값 설정)
        return memberRepository.findByPhoneNumber(phoneNumber)
            .orElseGet(() -> {
                Member newMember = Member.builder()
                    .phoneNumber(phoneNumber)
                    .nickname("유저_" + phoneNumber.substring(7))
                    .build();
                return memberRepository.save(newMember);
            });
    }
}