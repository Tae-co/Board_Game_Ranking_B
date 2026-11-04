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
 * 경기 신고 (3g). 신고한 사람은 관리자에게도 안 보인다 — {@code reporterMemberId}는 중복 막기에만 쓴다.
 * 6b는 (게임, 그 경기 1등) 묶음으로 본다.
 */
@Entity
@Table(name = "store_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreReport {

    public enum Reason { NOT_PLAYED, THROWING, OTHER }

    public enum Status { OPEN, RESET, CLOSED }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Long storeMatchId;

    @Column(nullable = false)
    private Long boardGameId;

    private Long targetMemberId;

    private Long reporterMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Reason reason;

    @Column(length = 300)
    private String memo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.OPEN;

    private LocalDateTime resolvedAt;

    @Column(length = 36)
    private String resolutionBatch;

    public StoreReport(Long storeId, Long storeMatchId, Long boardGameId, Long targetMemberId, Long reporterMemberId,
                       Reason reason, String memo, LocalDateTime now) {
        this.storeId = storeId;
        this.storeMatchId = storeMatchId;
        this.boardGameId = boardGameId;
        this.targetMemberId = targetMemberId;
        this.reporterMemberId = reporterMemberId;
        this.reason = reason;
        this.memo = memo;
        this.createdAt = now;
    }

    public void resolve(Status status, String batch, LocalDateTime now) {
        this.status = status;
        this.resolutionBatch = batch;
        this.resolvedAt = now;
    }
}
