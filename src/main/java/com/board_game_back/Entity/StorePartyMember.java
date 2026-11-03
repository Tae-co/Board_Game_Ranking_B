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

/** 그룹 소속. {@code leftAt == null}이면 지금 그룹에 있다 (한 사람은 한 번에 하나). */
@Entity
@Table(name = "store_party_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorePartyMember {

    /** CONTEST = 대회가 시작돼 잠시 빠짐, CONTEST_END = 대회가 끝나 대회 그룹이 해산됨 */
    public enum LeftReason { LEAVE, KICK, EXPIRED, DISBAND, MOVE, WITHDRAW, CONTEST, CONTEST_END }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long partyId;

    private Long memberId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    private LocalDateTime leftAt;

    @Column(length = 16)
    private String leftReason;

    public StorePartyMember(Long partyId, Long memberId, LocalDateTime now) {
        this.partyId = partyId;
        this.memberId = memberId;
        this.joinedAt = now;
    }

    public boolean isActive() {
        return leftAt == null;
    }

    public void leave(LeftReason reason, LocalDateTime now) {
        if (leftAt != null) return;
        leftAt = now;
        leftReason = reason.name();
    }
}
