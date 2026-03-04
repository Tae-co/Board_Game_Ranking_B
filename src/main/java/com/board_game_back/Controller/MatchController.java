package com.board_game_back.Controller;

import com.board_game_back.DTO.MatchDto;
import com.board_game_back.DTO.MatchDto.ResultResponse;
import com.board_game_back.Service.MatchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController // 이 클래스가 REST API 엔드포인트임을 선언
@RequestMapping("/api/matches") // 이 컨트롤러의 기본 URL 주소
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 🌟 React(보통 localhost:3000)에서 API를 호출할 수 있도록 CORS 허용
public class MatchController {

    private final MatchService matchService;

    // [POST] /api/matches
    // 게임 결과 제출 API
    @PostMapping
    public ResponseEntity<List<ResultResponse>> submitMatchResult(
        @RequestBody MatchDto.ResultRequest request) {

        // 1. 프론트엔드에서 넘어온 request 데이터를 Service로 넘겨서 랭킹 계산 및 DB 저장 실행
        List<MatchDto.ResultResponse> response = matchService.recordMatchResult(request);

        // 2. 점수 등락폭(+15, -8 등)이 담긴 결과를 React로 반환 (애니메이션용)
        return ResponseEntity.ok(response);
    }
}
