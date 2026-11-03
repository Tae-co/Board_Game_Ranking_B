package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하루 대회 (plan-store §1 이벤트·대회 점수·대회 시작). 시각은 UTC 벽시계.
 * RECRUITING → PLAYING → FINISHED, 시작 전엔 CANCELLED(STAFF 취소·그날 시작 안 됨·운영 끝냄).
 * 수정은 모집 중에만, 신청은 그대로 두고 덮어쓴다 (35차).
 */
@Entity
@Table(name = "store_contest")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreContest {

    public enum Status { RECRUITING, PLAYING, FINISHED, CANCELLED }

    public enum EndReason { COMPLETED, SHORT_HANDED, STAFF_CANCEL, DAY_OVER, STORE_CLOSED }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Long storeRoomId;

    @Column(nullable = false)
    private Long boardGameId;

    @Column(nullable = false, length = 40)
    private String title;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    @Column(nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int rounds;

    @Column(length = 80)
    private String prizeText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private EndReason endReason;

    private Long partyId;

    private Long recorderMemberId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    public StoreContest(Long storeId, LocalDateTime now) {
        this.storeId = storeId;
        this.status = Status.RECRUITING;
        this.createdAt = now;
    }

    public void update(Long storeRoomId, Long boardGameId, String title, LocalDateTime startsAt, LocalDate businessDate,
                       int capacity, int rounds, String prizeText) {
        this.storeRoomId = storeRoomId;
        this.boardGameId = boardGameId;
        this.title = title;
        this.startsAt = startsAt;
        this.businessDate = businessDate;
        this.capacity = capacity;
        this.rounds = rounds;
        this.prizeText = prizeText;
    }

    public boolean isRecruiting() {
        return status == Status.RECRUITING;
    }

    public boolean isPlaying() {
        return status == Status.PLAYING;
    }

    public void start(Long partyId, Long recorderMemberId, LocalDateTime now) {
        this.status = Status.PLAYING;
        this.partyId = partyId;
        this.recorderMemberId = recorderMemberId;
        this.startedAt = now;
    }

    public void finish(EndReason reason, LocalDateTime now) {
        end(Status.FINISHED, reason, now);
    }

    public void cancel(EndReason reason, LocalDateTime now) {
        end(Status.CANCELLED, reason, now);
    }

    private void end(Status status, EndReason reason, LocalDateTime now) {
        this.status = status;
        this.endReason = reason;
        this.endedAt = now;
    }

    public void changeRecorder(Long memberId) {
        this.recorderMemberId = memberId;
    }

    public void anonymizeRecorder() {
        this.recorderMemberId = null;
    }
}
