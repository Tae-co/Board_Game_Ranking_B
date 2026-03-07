package com.board_game_back.Controller;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Repository.BoardGameRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BoardGameController {

    private final BoardGameRepository boardGameRepository;

    // [GET] /api/games - 전체 보드게임 목록 조회
    @GetMapping
    public ResponseEntity<List<BoardGame>> getAllGames() {
        return ResponseEntity.ok(boardGameRepository.findAll());
    }
}