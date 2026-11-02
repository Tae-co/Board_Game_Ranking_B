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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매장 판. 시각은 UTC 벽시계. 저장 마감·영업 마감·기록 시간은 시작할 때 박아 둔다 —
 * 영업시간이나 게임 기록 시간을 바꿔도 열린 판은 시작할 때 값으로 끝까지 간다 (48차).
 *
 * <p>PLAYING → AWAITING_CONFIRM → (DISPUTED|PLAYING → AWAITING_CONFIRM)* → RECORDED / CANCELLED / EXPIRED.
 * 기록자가 "점수 고치기"를 누르면 PLAYING으로 돌아가되 {@code firstSavedAt}은 남는다.
 */
@Entity
@Table(name = "store_match")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreMatch {

    public enum Status { PLAYING, AWAITING_CONFIRM, DISPUTED, RECORDED, CANCELLED, EXPIRED }

    public enum EndReason { CANCELLED, EXPIRED_MAX, EXPIRED_CLOSE, CONFIRMED, TIMEOUT }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Long storeRoomId;

    @Column(nullable = false)
    private Long boardGameId;

    @Column(nullable = false)
    private Long partyId;

    private Long recorderMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false)
    private int minMinutes;

    @Column(nullable = false)
    private int maxMinutes;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private LocalDateTime saveDeadline;

    @Column(nullable = false)
    private LocalDateTime closesAt;

    private LocalDateTime firstSavedAt;

    private LocalDateTime confirmDeadline;

    private Long disputedByMemberId;

    @Column(columnDefinition = "TEXT")
    private String draftJson;

    @Column(nullable = false)
    private int draftVersion;

    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EndReason endReason;

    @Builder
    public StoreMatch(Long storeId, Long storeRoomId, Long boardGameId, Long partyId, Long recorderMemberId,
                      LocalDate businessDate, int minMinutes, int maxMinutes, LocalDateTime startedAt,
                      LocalDateTime saveDeadline, LocalDateTime closesAt) {
        this.storeId = storeId;
        this.storeRoomId = storeRoomId;
        this.boardGameId = boardGameId;
        this.partyId = partyId;
        this.recorderMemberId = recorderMemberId;
        this.businessDate = businessDate;
        this.minMinutes = minMinutes;
        this.maxMinutes = maxMinutes;
        this.startedAt = startedAt;
        this.saveDeadline = saveDeadline;
        this.closesAt = closesAt;
        this.status = Status.PLAYING;
    }

    public boolean isOpen() {
        return status == Status.PLAYING || status == Status.AWAITING_CONFIRM || status == Status.DISPUTED;
    }

    /** 점수판을 고칠 수 있는 상태 (처음 입력 · 점수가 달라요 받음 · 점수 고치기) */
    public boolean isEditable() {
        return status == Status.PLAYING || status == Status.DISPUTED;
    }

    public LocalDateTime saveOpensAt() {
        return startedAt.plusMinutes(minMinutes);
    }

    public void updateDraft(String draftJson) {
        this.draftJson = draftJson;
        this.draftVersion++;
    }

    public void submit(LocalDateTime now, LocalDateTime confirmDeadline) {
        if (firstSavedAt == null) firstSavedAt = now;
        this.status = Status.AWAITING_CONFIRM;
        this.confirmDeadline = confirmDeadline;
        this.disputedByMemberId = null;
    }

    public void dispute(Long memberId) {
        this.status = Status.DISPUTED;
        this.disputedByMemberId = memberId;
        this.confirmDeadline = null;
    }

    /** 기록자가 3i에서 "점수 고치기" */
    public void reopen() {
        this.status = Status.PLAYING;
        this.disputedByMemberId = null;
        this.confirmDeadline = null;
    }

    public void record(EndReason reason, LocalDateTime now) {
        end(Status.RECORDED, reason, now);
    }

    public void cancel(LocalDateTime now) {
        end(Status.CANCELLED, EndReason.CANCELLED, now);
    }

    public void expire(EndReason reason, LocalDateTime now) {
        end(Status.EXPIRED, reason, now);
    }

    private void end(Status status, EndReason reason, LocalDateTime now) {
        this.status = status;
        this.endReason = reason;
        this.endedAt = now;
        this.confirmDeadline = null;
    }

    public void anonymizeRecorder() {
        this.recorderMemberId = null;
    }
}
