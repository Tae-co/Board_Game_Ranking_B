package com.board_game_back.Service;

import com.board_game_back.DTO.EventDto;
import com.board_game_back.Entity.EventName;
import com.board_game_back.Entity.UserEvent;
import com.board_game_back.Repository.UserEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventService {

    /** 비로그인도 호출할 수 있는 엔드포인트라 문자열 길이를 서버에서 자른다. */
    private static final int MAX_PROPS_LENGTH = 2000;

    /** ObjectMapper 빈은 이 프로젝트에 없다 (AdminController도 동일하게 직접 만들어 쓴다). */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final UserEventRepository userEventRepository;

    /**
     * 계측은 제품 기능이 아니다. 실패해도 사용자 흐름에 영향이 없어야 하므로
     * 요청 스레드와 분리하고 예외를 삼킨다.
     */
    @Async
    public void record(EventDto.LogRequest request, Long memberId) {
        if (request == null) return;
        EventName eventName = parseEventName(request.eventName());
        if (eventName == null) return; // 화이트리스트 밖 — 조용히 버린다

        try {
            userEventRepository.save(UserEvent.builder()
                    .memberId(memberId)
                    .anonId(truncate(request.anonId(), 64))
                    .eventName(eventName)
                    .communityId(request.communityId())
                    .roomId(request.roomId())
                    .boardGameId(request.boardGameId())
                    .props(serializeProps(request.props()))
                    .sessionId(truncate(request.sessionId(), 64))
                    .platform(truncate(request.platform(), 16))
                    .appVersion(truncate(request.appVersion(), 20))
                    .build());
        } catch (Exception e) {
            log.warn("이벤트 기록 실패 (무시): {}", eventName, e);
        }
    }

    private EventName parseEventName(String raw) {
        if (raw == null) return null;
        try {
            return EventName.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String serializeProps(Object props) {
        if (props == null) return null;
        try {
            String json = OBJECT_MAPPER.writeValueAsString(props);
            return json.length() > MAX_PROPS_LENGTH ? null : json;
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
