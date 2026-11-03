package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대회 참가 신청. 신청 취소는 행을 지운다.
 * 시작 때 도착 확인된 사람은 PLAYING, 안 온 사람은 EXCLUDED. 대회 중 매장을 나가거나 탈퇴하면 LEFT —
 * 지금까지 판 점수는 남고 순위에서 빠진다 (plan-store §1 대회 시작).
 */
@Entity
@Table(name = "store_contest_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreContestEntry {

    public enum Status { APPLIED, PLAYING, EXCLUDED, LEFT }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long contestId;

    private Long memberId;

    @Column(nullable = false)
    private LocalDateTime appliedAt;

    private LocalDateTime arrivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    private Long originPartyId;

    private LocalDateTime leftAt;

    public StoreContestEntry(Long contestId, Long memberId, LocalDateTime now) {
        this.contestId = contestId;
        this.memberId = memberId;
        this.appliedAt = now;
        this.status = Status.APPLIED;
    }

    public boolean isArrived() {
        return arrivedAt != null;
    }

    public void setArrived(boolean arrived, LocalDateTime now) {
        this.arrivedAt = arrived ? (arrivedAt == null ? now : arrivedAt) : null;
    }

    public void play(Long originPartyId) {
        this.status = Status.PLAYING;
        this.originPartyId = originPartyId;
    }

    public void exclude() {
        this.status = Status.EXCLUDED;
    }

    public void leave(LocalDateTime now) {
        this.status = Status.LEFT;
        this.leftAt = now;
    }

    public void anonymize() {
        this.memberId = null;
    }
}
