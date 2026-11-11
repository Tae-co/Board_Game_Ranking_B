package com.board_game_back.Controller;

import com.board_game_back.DTO.BoardGameDto;
import com.board_game_back.DTO.MemberDto;
import com.board_game_back.Service.BoardGameService;
import com.board_game_back.Service.MemberService;
import com.board_game_back.Service.RoomService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final BoardGameService boardGameService;
    private final MemberService memberService;
    private final RoomService roomService;

    // SecurityConfig도 /api/admin/**에 ROLE_ADMIN을 요구한다. 설정이 바뀌어도 열리지 않게 한 번 더 막는다
    private void checkAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            throw new SecurityException("관리자 권한이 필요합니다.");
        }
    }

    /** 보드게임 목록 조회 */
    @GetMapping("/games")
    public ResponseEntity<List<BoardGameDto.Response>> getGames() {
        checkAdmin();
        return ResponseEntity.ok(boardGameService.getAllGamesForAdmin());
    }

    /** 보드게임 추가 */
    @PostMapping("/games")
    public ResponseEntity<BoardGameDto.Response> addGame(@RequestBody BoardGameDto.AdminRequest req) {
        checkAdmin();
        return ResponseEntity.ok(boardGameService.createOfficialGame(req));
    }

    /** 보드게임 수정 */
    @PutMapping("/games/{id}")
    public ResponseEntity<BoardGameDto.Response> updateGame(
            @PathVariable Long id,
            @RequestBody BoardGameDto.AdminRequest req) {
        checkAdmin();
        return ResponseEntity.ok(boardGameService.updateGameByAdmin(id, req));
    }

    /** 보드게임 삭제 */
    @DeleteMapping("/games/{id}")
    public ResponseEntity<String> deleteGame(@PathVariable Long id) {
        checkAdmin();
        boardGameService.deleteGameByAdmin(id);
        return ResponseEntity.ok("삭제되었습니다.");
    }

    /** 전체 멤버 목록 */
    @GetMapping("/members")
    public ResponseEntity<List<MemberDto.AdminSummary>> getMembers() {
        checkAdmin();
        return ResponseEntity.ok(memberService.getMembersForAdmin());
    }

    /** 멤버 삭제 */
    @DeleteMapping("/members/{id}")
    public ResponseEntity<String> deleteMember(@PathVariable Long id) {
        checkAdmin();
        roomService.deleteMember(id);
        return ResponseEntity.ok("삭제되었습니다.");
    }

    /** 게임 이미지 업로드 */
    @PostMapping("/upload-image")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam MultipartFile file) {
        checkAdmin();
        try {
            return ResponseEntity.ok(Map.of("url", boardGameService.uploadOfficialGameImage(file)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
