package com.board_game_back.Controller;

import com.board_game_back.DTO.MemberDto;
import com.board_game_back.DTO.SeasonDto;
import com.board_game_back.Service.AccessGuard;
import com.board_game_back.Service.MemberService;
import com.board_game_back.Service.SeasonArchiveService;
import com.board_game_back.Utils.NicknameRules;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class MemberController {

    private final MemberService memberService;
    private final SeasonArchiveService seasonArchiveService;
    private final AccessGuard accessGuard;

    @GetMapping("/search")
    public ResponseEntity<List<MemberDto.SearchItem>> searchMembers(
        @RequestParam(required = false, defaultValue = "") String nickname,
        @RequestParam(required = false) Long excludeId) {
        return ResponseEntity.ok(memberService.searchByNickname(nickname, excludeId));
    }

    @GetMapping("/{memberId}")
    public ResponseEntity<MemberDto.ProfileResponse> getMember(@PathVariable Long memberId) {
        return ResponseEntity.ok(memberService.getProfile(memberId));
    }

    @GetMapping("/{memberId}/stats")
    public ResponseEntity<MemberDto.StatsResponse> getMemberStats(@PathVariable Long memberId) {
        return ResponseEntity.ok(memberService.getStats(memberId));
    }

    /** 시즌별 내 점수 추이 (시즌 탭 ④). boardGameId를 주지 않으면 방의 게임으로 채운다. */
    @GetMapping("/{memberId}/season-history")
    public ResponseEntity<List<SeasonDto.SeasonHistoryItem>> getSeasonHistory(
            @PathVariable Long memberId,
            @RequestParam Long roomId,
            @RequestParam(required = false) Long boardGameId,
            @AuthenticationPrincipal Long requesterId) {
        accessGuard.requireRoomReader(roomId, requesterId);
        return ResponseEntity.ok(
            seasonArchiveService.getMemberSeasonHistory(memberId, roomId, boardGameId));
    }

    /** 프로필 트로피 선반. 시상 조건(참가자 3명 이상·본인 3경기 이상)을 통과한 1~3위만 나온다. */
    @GetMapping("/{memberId}/trophies")
    public ResponseEntity<List<SeasonDto.TrophyResponse>> getTrophies(@PathVariable Long memberId) {
        return ResponseEntity.ok(seasonArchiveService.getTrophies(memberId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteMember(
            @PathVariable Long id,
            @AuthenticationPrincipal Long requesterId) {
        if (!id.equals(requesterId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("본인 계정만 탈퇴할 수 있습니다.");
        }
        if (!memberService.exists(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("존재하지 않는 회원입니다.");
        }
        memberService.withdraw(id);
        return ResponseEntity.ok("탈퇴되었습니다.");
    }

    @PatchMapping("/{id}/profile-image")
    public ResponseEntity<Void> updateProfileImage(
            @PathVariable Long id,
            @RequestBody MemberDto.ProfileImageRequest body,
            @AuthenticationPrincipal Long requesterId) {
        if (!id.equals(requesterId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        memberService.updateProfileImage(id, body.profileImage());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/nickname")
    public ResponseEntity<Void> updateNickname(
            @PathVariable Long id,
            @RequestBody MemberDto.NicknameRequest body,
            @AuthenticationPrincipal Long requesterId) {
        if (!id.equals(requesterId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String nickname = body.nickname() == null ? null : body.nickname().trim();
        if (!NicknameRules.isValid(nickname)) {
            return ResponseEntity.badRequest().build();
        }
        memberService.updateNickname(id, nickname);
        return ResponseEntity.ok().build();
    }
}
