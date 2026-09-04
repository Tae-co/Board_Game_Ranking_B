package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.EventDto;
import com.board_game_back.Entity.EventName;
import com.board_game_back.Entity.UserEvent;
import com.board_game_back.Repository.UserEventRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserEventServiceTest {

    @Mock
    private UserEventRepository userEventRepository;

    @InjectMocks
    private UserEventService userEventService;

    private EventDto.LogRequest request(String eventName, Map<String, Object> props) {
        return new EventDto.LogRequest(eventName, "anon-1", 10L, 20L, 30L, props,
                "session-1", "ios", "1.9");
    }

    private UserEvent captureSaved() {
        ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
        verify(userEventRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void 화이트리스트에_있는_이벤트는_차원과_함께_저장된다() {
        userEventService.record(request("MATCH_SUBMITTED", Map.of("playerCount", 4)), 7L);

        UserEvent saved = captureSaved();
        assertThat(saved.getEventName()).isEqualTo(EventName.MATCH_SUBMITTED);
        assertThat(saved.getMemberId()).isEqualTo(7L);
        assertThat(saved.getCommunityId()).isEqualTo(10L);
        assertThat(saved.getRoomId()).isEqualTo(20L);
        assertThat(saved.getBoardGameId()).isEqualTo(30L);
        assertThat(saved.getProps()).contains("playerCount");
    }

    @Test
    void 비로그인_요청은_memberId_없이_저장된다() {
        // 초대 링크를 열었지만 가입하지 않은 사람을 측정하려면 이 경로가 살아 있어야 한다.
        userEventService.record(request("INVITE_LANDING_OPENED", null), null);

        assertThat(captureSaved().getMemberId()).isNull();
    }

    @Test
    void 화이트리스트_밖_이름은_저장하지_않는다() {
        // 구버전 앱이 폐기된 이벤트를 보내는 정상 상황이다. 예외가 아니라 무시여야 한다.
        userEventService.record(request("NOT_A_REAL_EVENT", null), 7L);

        verify(userEventRepository, never()).save(any());
    }

    @Test
    void eventName이_null이거나_요청이_null이면_저장하지_않는다() {
        userEventService.record(request(null, null), 7L);
        userEventService.record(null, 7L);

        verify(userEventRepository, never()).save(any());
    }

    @Test
    void props가_한도를_넘으면_props만_버리고_이벤트는_남긴다() {
        userEventService.record(request("GROUP_LOBBY_OPENED", Map.of("blob", "x".repeat(2100))), 7L);

        UserEvent saved = captureSaved();
        assertThat(saved.getProps()).isNull();
        assertThat(saved.getEventName()).isEqualTo(EventName.GROUP_LOBBY_OPENED);
    }

    @Test
    void 컬럼_길이를_넘는_문자열은_잘라서_저장한다() {
        // permitAll 엔드포인트라 값 길이를 클라이언트가 정하게 두면 안 된다.
        EventDto.LogRequest oversized = new EventDto.LogRequest(
                "APP_OPENED", "a".repeat(100), null, null, null, null,
                "s".repeat(100), "p".repeat(30), "v".repeat(40));

        userEventService.record(oversized, null);

        UserEvent saved = captureSaved();
        assertThat(saved.getAnonId()).hasSize(64);
        assertThat(saved.getSessionId()).hasSize(64);
        assertThat(saved.getPlatform()).hasSize(16);
        assertThat(saved.getAppVersion()).hasSize(20);
    }

    @Test
    void 서버전용_이벤트는_클라이언트가_보내도_저장하지_않는다() {
        // POST /api/events는 permitAll이다. 막지 않으면 누구나 탈퇴 지표를 부풀릴 수 있다.
        userEventService.record(request("MEMBER_DELETED", null), 7L);

        verify(userEventRepository, never()).save(any());
    }

    @Test
    void 서버측_기록은_memberId와_platform_server로_저장된다() {
        // 탈퇴는 프론트에서 못 찍는다 — 성공 직후 /login으로 넘어가며 요청이 취소된다.
        userEventService.recordServerSide(EventName.MEMBER_DELETED, 7L);

        UserEvent saved = captureSaved();
        assertThat(saved.getEventName()).isEqualTo(EventName.MEMBER_DELETED);
        assertThat(saved.getMemberId()).isEqualTo(7L);
        assertThat(saved.getPlatform()).isEqualTo("server");
        assertThat(saved.getAnonId()).isNull();
        assertThat(saved.getSessionId()).isNull();
    }

    @Test
    void 서버측_기록도_저장_실패를_삼킨다() {
        when(userEventRepository.save(any())).thenThrow(new RuntimeException("DB 장애"));

        // 여기서 예외가 새면 계측이 탈퇴 자체를 실패시킨다.
        assertThatCode(() -> userEventService.recordServerSide(EventName.MEMBER_DELETED, 7L))
                .doesNotThrowAnyException();
    }

    @Test
    void 프론트가_보내는_모든_이벤트_이름이_화이트리스트를_통과한다() {
        // 프론트 EVENTS 상수(api/services/events.js)와 1:1이어야 한다. 어긋나면 서버가
        // 202로 받고 조용히 버려서, 배포 후에도 "0건"인지 "이름 불일치"인지 알 수 없다.
        for (EventName name : EventName.values()) {
            if (name == EventName.MEMBER_DELETED) continue; // 서버 전용 (위 테스트가 담당)
            userEventService.record(request(name.name(), null), 7L);
        }

        verify(userEventRepository, org.mockito.Mockito.times(EventName.values().length - 1))
                .save(any());
    }

    @Test
    void 저장이_실패해도_예외가_호출부로_새지_않는다() {
        when(userEventRepository.save(any())).thenThrow(new RuntimeException("DB 장애"));

        assertThatCode(() -> userEventService.record(request("APP_OPENED", null), 7L))
                .doesNotThrowAnyException();
    }
}
