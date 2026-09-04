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
        userEventService.record(request("RANKING_VIEWED", Map.of("blob", "x".repeat(2100))), 7L);

        UserEvent saved = captureSaved();
        assertThat(saved.getProps()).isNull();
        assertThat(saved.getEventName()).isEqualTo(EventName.RANKING_VIEWED);
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
    void 저장이_실패해도_예외가_호출부로_새지_않는다() {
        when(userEventRepository.save(any())).thenThrow(new RuntimeException("DB 장애"));

        assertThatCode(() -> userEventService.record(request("APP_OPENED", null), 7L))
                .doesNotThrowAnyException();
    }
}
