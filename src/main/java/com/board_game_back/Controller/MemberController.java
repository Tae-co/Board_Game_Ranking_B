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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class MemberController {

    private final MemberRepository memberRepository;
    private final PlayerGameRatingRepository playerGameRatingRepository;
    private final RoomService roomService;

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

    @GetMapping("/{memberId}")
    public ResponseEntity<?> getMember(@PathVariable Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 멤버입니다."));
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

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteMember(
            @PathVariable Long id,
            @AuthenticationPrincipal Long requesterId) {
        if (!id.equals(requesterId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("본인 계정만 탈퇴할 수 있습니다.");
        }
        if (!memberRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("존재하지 않는 회원입니다.");
        }
        roomService.deleteMember(id);
        return ResponseEntity.ok("탈퇴되었습니다.");
    }

    @PatchMapping("/{id}/profile-image")
    @Transactional
    public ResponseEntity<Void> updateProfileImage(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Long requesterId) {
        if (!id.equals(requesterId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String profileImage = body.get("profileImage");
        Member member = memberRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        member.updateProfileImage(profileImage);
        memberRepository.save(member);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/nickname")
    @Transactional
    public ResponseEntity<Void> updateNickname(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Long requesterId) {
        if (!id.equals(requesterId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
