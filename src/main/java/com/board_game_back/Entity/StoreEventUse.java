package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 1회용 쿠폰 사용 기록. (event_id, member_id) 유니크 — 회원당 한 번. 매장을 나갔다 다시 가입해도 남는다 */
@Entity
@Table(name = "store_event_use")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreEventUse {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private LocalDateTime usedAt;

    public StoreEventUse(Long eventId, Long memberId, LocalDateTime usedAt) {
        this.eventId = eventId;
        this.memberId = memberId;
        this.usedAt = usedAt;
    }
}
