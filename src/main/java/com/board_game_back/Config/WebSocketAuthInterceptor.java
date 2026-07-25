package com.board_game_back.Config;

import com.board_game_back.Security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP CONNECT 시 Authorization: Bearer 토큰을 검증하고, 유효하면
 * Principal(=memberId)을 세션에 설정한다 (#19).
 * 이후 프레임(예: /app/presence/join)은 이 Principal로 신원을 판단하므로
 * 클라이언트가 페이로드에 넣은 memberId를 신뢰하지 않는다(스푸핑 차단).
 *
 * 비파괴 정책: 토큰이 없거나 무효면 Principal을 설정하지 않고 연결은 허용한다.
 * 신원이 필요한 처리(PresenceController.join)에서 Principal 부재 시 무시한다.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String auth = accessor.getFirstNativeHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7);
                if (jwtTokenProvider.validateToken(token)) {
                    Long memberId = jwtTokenProvider.getMemberIdFromToken(token);
                    // Principal은 함수형 인터페이스(getName) → memberId를 이름으로 사용
                    accessor.setUser(() -> String.valueOf(memberId));
                }
            }
        }
        return message;
    }
}
