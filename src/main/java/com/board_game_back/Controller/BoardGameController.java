package com.board_game_back.Controller;

import com.board_game_back.DTO.BoardGameDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.CommunityAdminRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BoardGameController {

    private static final int DEFAULT_MIN_PLAYERS = 2;
    private static final int DEFAULT_MAX_PLAYERS = 8;

    private final BoardGameRepository boardGameRepository;
    private final CommunityAdminRepository communityAdminRepository;

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
    @PostMapping
    public ResponseEntity<BoardGame> createCustomGame(@RequestBody BoardGameDto.CreateRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "게임 이름을 입력해주세요.");
        }
        if (req.communityId() == null || req.memberId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "커뮤니티 정보가 필요합니다.");
        }
        if (!communityAdminRepository.existsByCommunityIdAndMemberId(req.communityId(), req.memberId())) {
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
            .imageUrl("")
            .minPlayers(minPlayers)
            .maxPlayers(maxPlayers)
            .schemaJson(req.schemaJson())
            .communityId(req.communityId())
            .createdByMemberId(req.memberId())
            .build();

        return ResponseEntity.ok(boardGameRepository.save(game));
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
