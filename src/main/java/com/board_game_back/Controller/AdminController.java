package com.board_game_back.Controller;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
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

    private final BoardGameRepository boardGameRepository;
    private final MemberRepository memberRepository;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private void checkAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            throw new SecurityException("관리자 권한이 필요합니다.");
        }
    }

    /** 보드게임 목록 조회 */
    @GetMapping("/games")
    public ResponseEntity<List<BoardGame>> getGames() {
        checkAdmin();
        return ResponseEntity.ok(boardGameRepository.findAll());
    }

    /** 보드게임 추가 */
    @PostMapping("/games")
    public ResponseEntity<BoardGame> addGame(@RequestBody Map<String, Object> body) {
        checkAdmin();
        BoardGame game = BoardGame.builder()
                .name((String) body.get("name"))
                .imageUrl((String) body.getOrDefault("imageUrl", ""))
                .minPlayers((int) body.getOrDefault("minPlayers", 2))
                .maxPlayers((int) body.getOrDefault("maxPlayers", 6))
                .build();
        return ResponseEntity.ok(boardGameRepository.save(game));
    }

    /** 보드게임 수정 */
    @PutMapping("/games/{id}")
    @Transactional
    public ResponseEntity<BoardGame> updateGame(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        checkAdmin();
        BoardGame game = boardGameRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게임입니다."));
        game.update(
                (String) body.get("name"),
                (String) body.getOrDefault("imageUrl", ""),
                (int) body.getOrDefault("minPlayers", 2),
                (int) body.getOrDefault("maxPlayers", 6)
        );
        return ResponseEntity.ok(game);
    }

    /** 보드게임 삭제 */
    @DeleteMapping("/games/{id}")
    public ResponseEntity<String> deleteGame(@PathVariable Long id) {
        checkAdmin();
        boardGameRepository.deleteById(id);
        return ResponseEntity.ok("삭제되었습니다.");
    }

    /** 전체 멤버 목록 */
    @GetMapping("/members")
    public ResponseEntity<List<Member>> getMembers() {
        checkAdmin();
        return ResponseEntity.ok(memberRepository.findAll());
    }

    /** 멤버 삭제 */
    @DeleteMapping("/members/{id}")
    public ResponseEntity<String> deleteMember(@PathVariable Long id) {
        checkAdmin();
        memberRepository.deleteById(id);
        return ResponseEntity.ok("삭제되었습니다.");
    }

    /** 게임 이미지 업로드 */
    @PostMapping("/upload-image")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam MultipartFile file,
            HttpServletRequest request) throws IOException {
        checkAdmin();
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.'))
                : "";
        String filename = UUID.randomUUID() + ext;
        Files.copy(file.getInputStream(), uploadPath.resolve(filename));
        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        return ResponseEntity.ok(Map.of("url", baseUrl + "/api/images/" + filename));
    }
}
