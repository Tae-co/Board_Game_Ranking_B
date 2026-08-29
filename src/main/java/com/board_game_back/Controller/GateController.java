package com.board_game_back.Controller;

import com.board_game_back.DTO.GateDto;
import com.board_game_back.Service.GateEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gates")
@RequiredArgsConstructor
public class GateController {

    private final GateEventService gateEventService;

    /** 게이트 이벤트 기록. 실패해도 사용자 흐름을 막지 않으므로 프론트는 응답을 기다리지 않는다. */
    @PostMapping("/events")
    public ResponseEntity<Void> recordEvent(
            @RequestBody GateDto.EventRequest request,
            @AuthenticationPrincipal Long memberId) {
        gateEventService.record(request, memberId);
        return ResponseEntity.accepted().build();
    }
}
