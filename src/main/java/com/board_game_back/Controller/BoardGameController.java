package com.board_game_back.Controller;

import com.board_game_back.DTO.BoardGameDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.CommunityAdminRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.RoomRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class BoardGameController {

    private static final int DEFAULT_MIN_PLAYERS = 2;
    private static final int DEFAULT_MAX_PLAYERS = 8;
    private static final int MAX_ROOM_NAMES_IN_MESSAGE = 3;

    private final BoardGameRepository boardGameRepository;
    private final CommunityAdminRepository communityAdminRepository;
    private final RoomRepository roomRepository;
    private final MatchRecordRepository matchRecordRepository;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    // [GET] /api/games - 공식 게임 목록. communityId를 주면 그 커뮤니티의 커스텀 게임도 함께 내려준다.
    @GetMapping
    public ResponseEntity<List<BoardGame>> getAllGames(@RequestParam(required = false) Long communityId) {
        return ResponseEntity.ok(
            communityId == null
                ? boardGameRepository.findByCommunityIdIsNull()
                : boardGameRepository.findByCommunityIdIsNullOrCommunityId(communityId)
        );
    }

    // [GET] /api/games/{id} - 단건 조회.
    // 점수판이 스키마를 직접 가져올 때 쓴다. 커뮤니티 소속과 무관하게 id로 조회되므로
    // 초대 링크로 들어온 손님도 방의 커스텀 점수판을 그릴 수 있다.
    @GetMapping("/{id}")
    public ResponseEntity<BoardGame> getGame(@PathVariable Long id) {
        return boardGameRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 게임입니다."));
    }

    // [POST] /api/games - 커뮤니티 커스텀 점수판 생성 (커뮤니티 어드민만)
    // memberId는 JWT 토큰에서 꺼낸다. 본문으로 받으면 남의 memberId를 사칭할 수 있다.
    @PostMapping
    public ResponseEntity<BoardGame> createCustomGame(
            @AuthenticationPrincipal Long memberId,
            @RequestBody BoardGameDto.CreateRequest req) {
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "게임 이름을 입력해주세요.");
        }
        if (req.communityId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "커뮤니티 정보가 필요합니다.");
        }
        if (!communityAdminRepository.existsByCommunityIdAndMemberId(req.communityId(), memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "커뮤니티 어드민만 점수판을 만들 수 있습니다.");
        }

        String name = req.name().trim();
        if (boardGameRepository.existsByCommunityIdAndName(req.communityId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 같은 이름의 게임이 있습니다.");
        }
        validateUserSchema(req.schemaJson());

        int minPlayers = req.minPlayers() != null ? req.minPlayers() : DEFAULT_MIN_PLAYERS;
        int maxPlayers = req.maxPlayers() != null ? req.maxPlayers() : DEFAULT_MAX_PLAYERS;
        if (minPlayers < 1 || maxPlayers < minPlayers) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인원수 설정이 올바르지 않습니다.");
        }

        BoardGame game = BoardGame.builder()
            .name(name)
            .imageUrl(req.imageUrl() != null ? req.imageUrl() : "")
            .minPlayers(minPlayers)
            .maxPlayers(maxPlayers)
            .schemaJson(req.schemaJson())
            .communityId(req.communityId())
            .createdByMemberId(memberId)
            .build();

        return ResponseEntity.ok(boardGameRepository.save(game));
    }

    // [DELETE] /api/games/{id} - 커스텀 점수판 삭제 (만든 커뮤니티의 어드민만)
    // 공식 게임은 지울 수 없고, 이미 방이나 플레이 기록이 붙어 있으면 거절한다.
    // (Room.boardGameId와 MatchRecord.boardGame이 이 행을 참조하므로 지우면 깨진다)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomGame(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        BoardGame game = boardGameRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 게임입니다."));

        if (game.getCommunityId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "기본 제공 게임은 삭제할 수 없습니다.");
        }
        if (!communityAdminRepository.existsByCommunityIdAndMemberId(game.getCommunityId(), memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "커뮤니티 어드민만 삭제할 수 있습니다.");
        }
        // 어떤 그룹이 쓰고 있는지 이름까지 알려준다. "삭제할 수 없다"만 알려주면
        // 사용자가 무엇을 해야 하는지 알 수 없다.
        List<Room> roomsUsingGame = roomRepository.findByBoardGameId(id);
        if (!roomsUsingGame.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, buildRoomsInUseMessage(roomsUsingGame));
        }
        if (matchRecordRepository.existsByBoardGameId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "플레이 기록이 남아 있어 삭제할 수 없어요.");
        }

        boardGameRepository.delete(game);
        return ResponseEntity.noContent().build();
    }

    // [POST] /api/games/upload-image - 커스텀 게임 썸네일 업로드 (커뮤니티 어드민만)
    // 어드민 전용 /api/admin/upload-image는 ROLE_ADMIN을 요구해서 커뮤니티 어드민이 쓸 수 없다.
    @PostMapping("/upload-image")
    public ResponseEntity<Map<String, String>> uploadGameImage(
            @AuthenticationPrincipal Long memberId,
            @RequestParam MultipartFile file,
            @RequestParam Long communityId) {
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (!communityAdminRepository.existsByCommunityIdAndMemberId(communityId, memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "커뮤니티 어드민만 이미지를 올릴 수 있습니다.");
        }

        // 공개 버킷이므로 실제 이미지인지 확인한다
        String contentType = file.getContentType();
        if (file.isEmpty() || contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일만 올릴 수 있습니다.");
        }

        try {
            String extension = switch (contentType) {
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                case "image/gif" -> ".gif";
                default -> ".jpg";
            };
            String filename = UUID.randomUUID() + extension;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + serviceRoleKey);
            headers.setContentType(MediaType.parseMediaType(contentType));

            new RestTemplate().exchange(
                supabaseUrl + "/storage/v1/object/game-images/" + filename,
                HttpMethod.PUT,
                new HttpEntity<>(file.getBytes(), headers),
                String.class
            );

            return ResponseEntity.ok(Map.of(
                "url", supabaseUrl + "/storage/v1/object/public/game-images/" + filename
            ));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다.");
        }
    }

    /** "삭제할 수 없다"로 끝내지 않고, 어떤 그룹을 지워야 하는지까지 알려준다. */
    private String buildRoomsInUseMessage(List<Room> rooms) {
        int shown = Math.min(rooms.size(), MAX_ROOM_NAMES_IN_MESSAGE);
        String names = rooms.stream()
            .limit(shown)
            .map(room -> "'" + room.getName() + "'")
            .collect(Collectors.joining(", "));
        String rest = rooms.size() > shown
            ? " 외 " + (rooms.size() - shown) + "개"
            : "";
        return names + rest + " 그룹에서 사용 중이에요.\n그룹을 먼저 삭제한 뒤 점수판을 삭제해주세요.";
    }

    /** 사용자가 만드는 점수판은 simple(순위만) / flat(항목별 점수) 두 가지만 허용한다. */
    private void validateUserSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "점수판 정보가 없습니다.");
        }

        Map<?, ?> parsed;
        try {
            parsed = new ObjectMapper().readValue(schemaJson, Map.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "점수판 형식이 올바르지 않습니다.");
        }

        String type = parsed.get("type") instanceof String s ? s : null;
        if (!"simple".equals(type) && !"flat".equals(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 점수판 형식입니다.");
        }
        if (!"flat".equals(type)) {
            return;
        }

        if (!(parsed.get("categories") instanceof List<?> categories) || categories.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "점수 항목을 1개 이상 추가해주세요.");
        }
        for (Object category : categories) {
            if (!(category instanceof Map<?, ?> c)
                || !(c.get("label") instanceof String label)
                || label.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "항목 이름을 모두 입력해주세요.");
            }
        }
    }
}
