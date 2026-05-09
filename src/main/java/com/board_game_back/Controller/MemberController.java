package com.board_game_back.Controller;

import com.board_game_back.DTO.MemberDto;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Service.RoomService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class MemberController {

    private final MemberRepository memberRepository;
    private final PlayerGameRatingRepository playerGameRatingRepository;
    private final RoomService roomService;

    // 닉네임 검색 (커뮤니티 어드민 추가용)
    // nickname 없거나 빈 값이면 전체 멤버 반환 (최대 100명)
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchMembers(
        @RequestParam(required = false, defaultValue = "") String nickname,
        @RequestParam(required = false) Long excludeId) {
        List<Member> source = nickname.isBlank()
            ? memberRepository.findAll()
            : memberRepository.findByNicknameContainingIgnoreCase(nickname);
        List<Map<String, Object>> results = source.stream()
            .filter(m -> excludeId == null || !m.getId().equals(excludeId))
            .limit(100)
            .map(m -> Map.<String, Object>of("memberId", m.getId(), "nickname", m.getNickname()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }

    // 멤버 조회
    @GetMapping("/{memberId}")
    public ResponseEntity<?> getMember(@PathVariable Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new RuntimeException("멤버를 찾을 수 없습니다."));
        return ResponseEntity.ok(
            new MemberDto.ProfileResponse(
                member.getId(),
                member.getNickname(),
                member.getOverallStats().getDisplayScore(),
                member.getOverallStats().getRatingDeviation(),
                member.getProfileImage()
            )
        );
    }

    // 플레이 통계 조회
    @GetMapping("/{memberId}/stats")
    @Transactional(readOnly = true)
    public ResponseEntity<MemberDto.StatsResponse> getMemberStats(@PathVariable Long memberId) {
        List<PlayerGameRating> ratings = playerGameRatingRepository.findPlayedByMemberId(memberId);

        int totalPlay = ratings.stream().mapToInt(PlayerGameRating::getPlayCount).sum();
        int totalWin  = ratings.stream().mapToInt(PlayerGameRating::getWinCount).sum();
        int totalLose = ratings.stream().mapToInt(PlayerGameRating::getLoseCount).sum();
        List<MemberDto.GameStatItem> games = ratings.stream()
            .map(r -> new MemberDto.GameStatItem(
                r.getBoardGame().getName(),
                r.getPlayCount(), r.getWinCount(), r.getLoseCount()
            ))
            .collect(Collectors.toList());

        return ResponseEntity.ok(new MemberDto.StatsResponse(totalPlay, totalWin, totalLose, games));
    }

    // 회원 탈퇴
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteMember(@PathVariable Long id) {
        try {
            if (!memberRepository.existsById(id)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("존재하지 않는 회원입니다.");
            }
            roomService.deleteMember(id);
            return ResponseEntity.ok("탈퇴되었습니다.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("회원 탈퇴 실패 memberId={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage()
                    : "회원 탈퇴 처리 중 서버 오류가 발생했습니다.");
        }
    }

    // 프로필 이미지 변경
    @PatchMapping("/{id}/profile-image")
    @Transactional
    public ResponseEntity<Void> updateProfileImage(
        @PathVariable Long id,
        @RequestBody Map<String, String> body) {
        String profileImage = body.get("profileImage");
        Member member = memberRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        member.updateProfileImage(profileImage);
        memberRepository.save(member);
        return ResponseEntity.ok().build();
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

}
