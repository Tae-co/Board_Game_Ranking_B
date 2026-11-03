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
 *
 * <p>대회 판({@code contestId != null})은 참가자 확인 뒤 AWAITING_STAFF로 가고, STAFF가 확인하면 RECORDED,
 * 돌려보내면 DISPUTED. 기록 시간·영업 마감 제한과 게임 취소가 없고 매장 Elo에 안 들어간다 (plan-store §1 대회 시작).
 */
@Entity
@Table(name = "store_match")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreMatch {

    public enum Status { PLAYING, AWAITING_CONFIRM, DISPUTED, AWAITING_STAFF, RECORDED, CANCELLED, EXPIRED }

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

    private Long contestId;

    private Integer contestRound;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EndReason endReason;

    @Builder
    public StoreMatch(Long storeId, Long storeRoomId, Long boardGameId, Long partyId, Long recorderMemberId,
                      LocalDate businessDate, int minMinutes, int maxMinutes, LocalDateTime startedAt,
                      LocalDateTime saveDeadline, LocalDateTime closesAt, Long contestId, Integer contestRound) {
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
        this.contestId = contestId;
        this.contestRound = contestRound;
        this.status = Status.PLAYING;
    }

    public boolean isContest() {
        return contestId != null;
    }

    public boolean isOpen() {
        return status == Status.PLAYING || status == Status.AWAITING_CONFIRM || status == Status.DISPUTED
            || status == Status.AWAITING_STAFF;
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

    /** 대회 판: 참가자 확인(전원 또는 5분)이 끝나 STAFF 확인(5c)을 기다린다 */
    public void awaitStaff() {
        this.status = Status.AWAITING_STAFF;
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

    /** 대회 판: 저장 전에 기록자가 대회에서 빠지면 그룹의 새 기록자로 */
    public void changeRecorder(Long memberId) {
        this.recorderMemberId = memberId;
    }

    public void anonymizeRecorder() {
        this.recorderMemberId = null;
    }
}
