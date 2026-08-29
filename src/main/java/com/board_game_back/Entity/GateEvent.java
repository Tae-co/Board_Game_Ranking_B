package com.board_game_back.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 페이월 게이트에서 발생한 이벤트 한 건. 결제 로직은 없고 측정만 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GateEvent {

    /** HIT=게이트에 막힘, INTEREST=관심 등록. 전환율 = INTEREST / HIT */
    public enum Action { HIT, INTEREST }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    private Long communityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private GateKey gateKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Action action;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

    public GateEvent(Long memberId, Long communityId, GateKey gateKey, Action action) {
        this.memberId = memberId;
        this.communityId = communityId;
        this.gateKey = gateKey;
        this.action = action;
    }
}
