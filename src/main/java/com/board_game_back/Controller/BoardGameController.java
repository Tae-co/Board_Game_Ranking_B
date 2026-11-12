package com.board_game_back.Controller;

import com.board_game_back.Exception.AppException;
import com.board_game_back.Exception.ErrorCode;
import com.board_game_back.DTO.BoardGameDto;
import com.board_game_back.DTO.UploadDto;
import com.board_game_back.Service.BoardGameService;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class BoardGameController {

    private final BoardGameService boardGameService;

    // [GET] /api/games - 공식 게임 목록. communityId를 주면 그 커뮤니티의 커스텀 게임도 함께 내려준다.
    @GetMapping
    public ResponseEntity<List<BoardGameDto.Response>> getAllGames(@RequestParam(required = false) Long communityId) {
        return ResponseEntity.ok(boardGameService.getGames(communityId));
    }

    // [GET] /api/games/{id} - 단건 조회 (초대 링크로 들어온 손님도 커스텀 점수판을 그릴 수 있게 소속 무관)
    @GetMapping("/{id}")
    public ResponseEntity<BoardGameDto.Response> getGame(@PathVariable Long id) {
        return ResponseEntity.ok(boardGameService.getGame(id));
    }

    // [POST] /api/games - 커뮤니티 커스텀 점수판 생성 (커뮤니티 어드민만)
    // memberId는 JWT 토큰에서 꺼낸다. 본문으로 받으면 남의 memberId를 사칭할 수 있다.
    @PostMapping
    public ResponseEntity<BoardGameDto.Response> createCustomGame(
            @AuthenticationPrincipal Long memberId,
            @RequestBody BoardGameDto.CreateRequest req) {
        requireLogin(memberId);
        return ResponseEntity.ok(boardGameService.createCustomGame(memberId, req));
    }

    // [DELETE] /api/games/{id} - 커스텀 점수판 삭제 (만든 커뮤니티의 어드민만)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomGame(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        requireLogin(memberId);
        boardGameService.deleteCustomGame(memberId, id);
        return ResponseEntity.noContent().build();
    }

    // [POST] /api/games/upload-image - 커스텀 게임 썸네일 업로드 (커뮤니티 어드민만)
    @PostMapping("/upload-image")
    public ResponseEntity<UploadDto.Response> uploadGameImage(
            @AuthenticationPrincipal Long memberId,
            @RequestParam MultipartFile file,
            @RequestParam Long communityId) {
        requireLogin(memberId);
        return ResponseEntity.ok(UploadDto.Response.ok(boardGameService.uploadGameImage(memberId, communityId, file)));
    }

    private void requireLogin(Long memberId) {
        if (memberId == null) {
            throw new AppException(ErrorCode.LOGIN_REQUIRED);
        }
    }
}
