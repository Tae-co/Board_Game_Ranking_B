package com.board_game_back.Controller;

import com.board_game_back.Service.SeasonRolloverService;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 롤오버 수동 트리거. 한 달에 한 번 도는 코드를 실제 데이터에서 확인할 유일한 수단이다.
 * 멱등이라 여러 번 눌러도 안전하다.
 */
@RestController
@RequestMapping("/api/seasons/admin")
@RequiredArgsConstructor
public class SeasonAdminController {

    private final SeasonRolloverService rolloverService;

    /** period 형식: yyyy-MM (마감할 시즌, 예: 2026-09) */
    @PostMapping("/rollover")
    public ResponseEntity<String> rollover(
            @RequestParam Long communityId,
            @RequestParam String period) {
        YearMonth season;
        try {
            season = YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body("시즌 형식이 잘못되었습니다. (예: 2026-09)");
        }
        int rolled = rolloverService.rollover(communityId, season);
        return ResponseEntity.ok("마감된 방 " + rolled + "개");
    }
}
