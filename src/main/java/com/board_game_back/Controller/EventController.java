package com.board_game_back.Controller;

import com.board_game_back.DTO.EventDto;
import com.board_game_back.Service.UserEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final UserEventService userEventService;

    /**
     * 행동 이벤트 기록. 프론트는 응답을 기다리지 않는다.
     *
     * <p>비로그인 요청도 받는다 — 초대 링크를 열었지만 가입하지 않은 사람이
     * 초대 퍼널에서 가장 알고 싶은 구간이기 때문이다. 그 경우 memberId는 null이고
     * anonId로만 식별된다.
     */
    @PostMapping
    public ResponseEntity<Void> log(@RequestBody EventDto.LogRequest request,
            Authentication authentication) {
        Long memberId = (authentication != null && authentication.getPrincipal() instanceof Long id)
                ? id : null;
        userEventService.record(request, memberId);
        return ResponseEntity.accepted().build();
    }
}
