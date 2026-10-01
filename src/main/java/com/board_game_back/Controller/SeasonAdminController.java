package com.board_game_back.Controller;

import com.board_game_back.Service.SeasonRolloverScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 롤오버 수동 트리거. 정각 스케줄러를 기다리지 않고 종료 시각이 지난 시즌을 지금 마감한다.
 * 멱등이라 여러 번 눌러도 안전하다.
 */
@RestController
@RequestMapping("/api/seasons/admin")
@RequiredArgsConstructor
public class SeasonAdminController {

    private final SeasonRolloverScheduler rolloverScheduler;

    @PostMapping("/rollover")
    public ResponseEntity<String> rollover() {
        return ResponseEntity.ok("마감된 시즌 " + rolloverScheduler.rolloverDueSeasons() + "개");
    }
}
