package com.board_game_back.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 행동 이벤트 한 건. 제품 로직은 없고 측정만 한다.
 *
 * <p>{@code memberId}는 NULL일 수 있다 — 초대 링크를 연 비로그인 방문자를 측정해야
 * 초대 퍼널의 앞단이 보인다. 그때는 {@code anonId}(기기 UUID)로 이어 붙인다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    @Column(length = 64)
    private String anonId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EventName eventName;

    private Long communityId;

    private Long roomId;

    private Long boardGameId;

    /** 이벤트별 가변 속성. 차원을 하나 추가할 때마다 마이그레이션하지 않으려고 둔다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String props;

    @Column(length = 64)
    private String sessionId;

    @Column(length = 16)
    private String platform;

    @Column(length = 20)
    private String appVersion;

    /** match_record.played_at과 같은 UTC. 시간대가 다르면 이벤트와 매치를 함께 볼 수 없다. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    @Builder
    public UserEvent(Long memberId, String anonId, EventName eventName, Long communityId,
            Long roomId, Long boardGameId, String props, String sessionId,
            String platform, String appVersion) {
        this.memberId = memberId;
        this.anonId = anonId;
        this.eventName = eventName;
        this.communityId = communityId;
        this.roomId = roomId;
        this.boardGameId = boardGameId;
        this.props = props;
        this.sessionId = sessionId;
        this.platform = platform;
        this.appVersion = appVersion;
    }
}
