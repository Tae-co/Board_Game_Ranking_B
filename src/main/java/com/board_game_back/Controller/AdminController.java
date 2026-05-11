package com.board_game_back.Controller;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Service.RoomService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
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
    private final RoomService roomService;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

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

    private void validateSchemaJson(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) return;
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<?, ?> parsed = mapper.readValue(schemaJson, Map.class);
            String type = (String) parsed.get("type");
            if (!List.of("flat", "sectioned", "conditional").contains(type)) {
                throw new IllegalArgumentException("유효하지 않은 schema type: " + type);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("schema_json 파싱 오류: " + e.getMessage());
        }
    }

    /** 보드게임 추가 */
    @PostMapping("/games")
    public ResponseEntity<BoardGame> addGame(@RequestBody Map<String, Object> body) {
        checkAdmin();
        String schemaJson = (String) body.get("schemaJson");
        validateSchemaJson(schemaJson);
        BoardGame game = BoardGame.builder()
                .name((String) body.get("name"))
                .imageUrl((String) body.getOrDefault("imageUrl", ""))
                .minPlayers((int) body.getOrDefault("minPlayers", 2))
                .maxPlayers((int) body.getOrDefault("maxPlayers", 6))
                .schemaJson(schemaJson)
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
        String schemaJson = (String) body.get("schemaJson");
        validateSchemaJson(schemaJson);
        BoardGame game = boardGameRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게임입니다."));
        game.update(
                (String) body.get("name"),
                (String) body.getOrDefault("imageUrl", ""),
                (int) body.getOrDefault("minPlayers", 2),
                (int) body.getOrDefault("maxPlayers", 6),
                schemaJson
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
    public ResponseEntity<List<Map<String, Object>>> getMembers() {
        checkAdmin();
        List<Map<String, Object>> result = memberRepository.findAll().stream()
            .map(m -> Map.<String, Object>of(
                "memberId", m.getId(),
                "nickname", m.getNickname() != null ? m.getNickname() : "",
                "role", m.getRole() != null ? m.getRole() : "",
                "phoneNumber", m.getPhoneNumber() != null ? m.getPhoneNumber() : ""
            ))
            .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(result);
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
            String filename = UUID.randomUUID() + ".jpg";
            String uploadUrl = supabaseUrl + "/storage/v1/object/game-images/" + filename;

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + serviceRoleKey);
            headers.setContentType(MediaType.IMAGE_JPEG);

            restTemplate.exchange(
                uploadUrl,
                HttpMethod.PUT,
                new HttpEntity<>(file.getBytes(), headers),
                String.class
            );

            String publicUrl = supabaseUrl + "/storage/v1/object/public/game-images/" + filename;
            return ResponseEntity.ok(Map.of("url", publicUrl));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
