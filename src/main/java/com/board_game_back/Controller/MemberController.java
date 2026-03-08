package com.board_game_back.Controller;

import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.MemberRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MemberController {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    // 멤버 조회
    @GetMapping("/{memberId}")
    public ResponseEntity<?> getMember(@PathVariable Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new RuntimeException("멤버를 찾을 수 없습니다."));
        return ResponseEntity.ok(Map.of(
            "memberId", member.getId(),
            "nickname", member.getNickname(),
            "phoneNumber", member.getPhoneNumber(),
            "role", member.getRole()
        ));
    }

    // 닉네임 변경
    @PatchMapping("/{id}/nickname")
    @Transactional
    public ResponseEntity<Void> updateNickname(
        @PathVariable Long id,
        @RequestBody Map<String, String> body) {
        String nickname = body.get("nickname");
        if (nickname == null || nickname.isBlank() || nickname.length() < 2) {
            return ResponseEntity.badRequest().build();
        }
        Member member = memberRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        member.updateNickname(nickname.trim());
        return ResponseEntity.ok().build();
    }

    // 비밀번호 변경
    @PatchMapping("/{id}/password")
    @Transactional
    public ResponseEntity<Map<String, String>> updatePassword(
        @PathVariable Long id,
        @RequestBody Map<String, String> body) {
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");

        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "새 비밀번호는 6자 이상이어야 합니다."));
        }

        Member member = memberRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        if (member.getPassword() != null && !member.getPassword().isBlank()) {
            if (currentPassword == null || !passwordEncoder.matches(currentPassword,
                member.getPassword())) {
                return ResponseEntity.status(401).body(Map.of("message", "현재 비밀번호가 올바르지 않습니다."));
            }
        }

        member.updatePassword(passwordEncoder.encode(newPassword));
        return ResponseEntity.ok().build();
    }
}
