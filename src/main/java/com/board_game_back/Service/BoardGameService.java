package com.board_game_back.Service;

import com.board_game_back.DTO.BoardGameDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.RatingType;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 게임(점수판) 조회와 커뮤니티 커스텀 점수판 생성·삭제·썸네일 업로드 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardGameService {

    private static final int DEFAULT_MIN_PLAYERS = 2;
    private static final int DEFAULT_MAX_PLAYERS = 8;
    private static final int MAX_ROOM_NAMES_IN_MESSAGE = 3;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BoardGameRepository boardGameRepository;
    private final CommunityAdminRepository communityAdminRepository;
    private final RoomRepository roomRepository;
    private final MatchRecordRepository matchRecordRepository;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    /** 공식 게임 목록. communityId를 주면 그 커뮤니티의 커스텀 게임도 함께 */
    public List<BoardGameDto.Response> getGames(Long communityId) {
        List<BoardGame> games = communityId == null
            ? boardGameRepository.findByCommunityIdIsNull()
            : boardGameRepository.findByCommunityIdIsNullOrCommunityId(communityId);
        return games.stream().map(BoardGameDto.Response::from).toList();
    }

    /**
     * 단건 조회. 점수판이 스키마를 직접 가져올 때 쓴다. 커뮤니티 소속과 무관하게 id로 조회되므로
     * 초대 링크로 들어온 손님도 방의 커스텀 점수판을 그릴 수 있다.
     */
    public BoardGameDto.Response getGame(Long id) {
        return BoardGameDto.Response.from(findGame(id));
    }

    /** 커뮤니티 커스텀 점수판 생성 (커뮤니티 어드민만) */
    @Transactional
    public BoardGameDto.Response createCustomGame(Long memberId, BoardGameDto.CreateRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "게임 이름을 입력해주세요.");
        }
        if (req.communityId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "커뮤니티 정보가 필요합니다.");
        }
        requireCommunityAdmin(req.communityId(), memberId, "커뮤니티 어드민만 점수판을 만들 수 있습니다.");

        String name = req.name().trim();
        if (boardGameRepository.existsByCommunityIdAndName(req.communityId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 같은 이름의 게임이 있습니다.");
        }
        String schemaType = validateUserSchema(req.schemaJson());

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
            .ratingType("chip".equals(schemaType) ? RatingType.CHIP : RatingType.ELO)
            .build();
        return BoardGameDto.Response.from(boardGameRepository.save(game));
    }

    /**
     * 커스텀 점수판 삭제 (만든 커뮤니티의 어드민만). 공식 게임은 지울 수 없고,
     * 이미 방이나 플레이 기록이 붙어 있으면 거절한다 (Room.boardGameId와 MatchRecord.boardGame이 이 행을 참조한다).
     */
    @Transactional
    public void deleteCustomGame(Long memberId, Long id) {
        BoardGame game = findGame(id);
        if (game.getCommunityId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "기본 제공 게임은 삭제할 수 없습니다.");
        }
        requireCommunityAdmin(game.getCommunityId(), memberId, "커뮤니티 어드민만 삭제할 수 있습니다.");

        // 어떤 그룹이 쓰고 있는지 이름까지 알려준다. "삭제할 수 없다"만 알려주면 사용자가 무엇을 해야 하는지 알 수 없다.
        List<Room> roomsUsingGame = roomRepository.findByBoardGameId(id);
        if (!roomsUsingGame.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, buildRoomsInUseMessage(roomsUsingGame));
        }
        if (matchRecordRepository.existsByBoardGameId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "플레이 기록이 남아 있어 삭제할 수 없어요.");
        }
        boardGameRepository.delete(game);
    }

    /**
     * 커스텀 게임 썸네일 업로드 (커뮤니티 어드민만).
     * 어드민 전용 /api/admin/upload-image는 ROLE_ADMIN을 요구해서 커뮤니티 어드민이 쓸 수 없다.
     *
     * @return 공개 URL
     */
    public String uploadGameImage(Long memberId, Long communityId, MultipartFile file) {
        requireCommunityAdmin(communityId, memberId, "커뮤니티 어드민만 이미지를 올릴 수 있습니다.");

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
            return supabaseUrl + "/storage/v1/object/public/game-images/" + filename;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다.");
        }
    }

    // ── 관리자: 공식 게임 관리 (/api/admin/games) ──

    private static final int ADMIN_DEFAULT_MIN_PLAYERS = 2;
    private static final int ADMIN_DEFAULT_MAX_PLAYERS = 6;
    private static final List<String> ADMIN_SCHEMA_TYPES = List.of("flat", "sectioned", "conditional", "simple");

    /** 모든 게임 (공식 + 커뮤니티 커스텀) */
    public List<BoardGameDto.Response> getAllGamesForAdmin() {
        return boardGameRepository.findAll().stream().map(BoardGameDto.Response::from).toList();
    }

    @Transactional
    public BoardGameDto.Response createOfficialGame(BoardGameDto.AdminRequest req) {
        validateAdminSchema(req.schemaJson());
        BoardGame game = BoardGame.builder()
            .name(req.name())
            .imageUrl(req.imageUrl() != null ? req.imageUrl() : "")
            .minPlayers(req.minPlayers() != null ? req.minPlayers() : ADMIN_DEFAULT_MIN_PLAYERS)
            .maxPlayers(req.maxPlayers() != null ? req.maxPlayers() : ADMIN_DEFAULT_MAX_PLAYERS)
            .schemaJson(req.schemaJson())
            .build();
        return BoardGameDto.Response.from(boardGameRepository.save(game));
    }

    @Transactional
    public BoardGameDto.Response updateGameByAdmin(Long id, BoardGameDto.AdminRequest req) {
        validateAdminSchema(req.schemaJson());
        BoardGame game = boardGameRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게임입니다."));
        game.update(
            req.name(),
            req.imageUrl() != null ? req.imageUrl() : "",
            req.minPlayers() != null ? req.minPlayers() : ADMIN_DEFAULT_MIN_PLAYERS,
            req.maxPlayers() != null ? req.maxPlayers() : ADMIN_DEFAULT_MAX_PLAYERS,
            req.schemaJson()
        );
        return BoardGameDto.Response.from(game);
    }

    @Transactional
    public void deleteGameByAdmin(Long id) {
        boardGameRepository.deleteById(id);
    }

    /** 공식 게임 썸네일 업로드. 실패하면 원인 예외를 그대로 던진다 (컨트롤러가 {"error": 메시지}로 응답) */
    public String uploadOfficialGameImage(MultipartFile file) throws Exception {
        String filename = UUID.randomUUID() + ".jpg";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceRoleKey);
        headers.setContentType(MediaType.IMAGE_JPEG);

        new RestTemplate().exchange(
            supabaseUrl + "/storage/v1/object/game-images/" + filename,
            HttpMethod.PUT,
            new HttpEntity<>(file.getBytes(), headers),
            String.class
        );
        return supabaseUrl + "/storage/v1/object/public/game-images/" + filename;
    }

    /** 관리자는 커스텀 점수판(simple)뿐 아니라 sectioned·conditional 같은 내장 형식도 쓴다. 비어 있으면 통과 */
    private void validateAdminSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) return;
        Map<?, ?> parsed;
        try {
            parsed = OBJECT_MAPPER.readValue(schemaJson, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("schema_json 파싱 오류: " + e.getMessage());
        }
        Object type = parsed.get("type");
        if (!ADMIN_SCHEMA_TYPES.contains(type)) {
            throw new IllegalArgumentException("유효하지 않은 schema type: " + type);
        }
    }

    private BoardGame findGame(Long id) {
        return boardGameRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 게임입니다."));
    }

    private void requireCommunityAdmin(Long communityId, Long memberId, String message) {
        if (!communityAdminRepository.existsByCommunityIdAndMemberId(communityId, memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
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

    /**
     * 사용자가 만드는 점수판은 simple(순위만) / flat(항목별 점수) / outcome(승무패만) / chip(바이인 손익)만 허용한다.
     *
     * @return 점수판 type. chip이면 랭킹도 Elo 대신 손익 누적이다.
     */
    private String validateUserSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "점수판 정보가 없습니다.");
        }

        Map<?, ?> parsed;
        try {
            parsed = OBJECT_MAPPER.readValue(schemaJson, Map.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "점수판 형식이 올바르지 않습니다.");
        }

        String type = parsed.get("type") instanceof String s ? s : null;
        if (!"simple".equals(type) && !"flat".equals(type) && !"outcome".equals(type) && !"chip".equals(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 점수판 형식입니다.");
        }
        if (!"flat".equals(type)) {
            return type;
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
        return type;
    }
}
