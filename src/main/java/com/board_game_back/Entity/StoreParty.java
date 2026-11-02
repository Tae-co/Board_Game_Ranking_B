package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 오늘의 그룹 (plan-store §2.1 3번). 만든 사람이 기록자이고, 기록자가 빠지면 가장 먼저 합류한 사람에게 넘어간다.
 * 판·그룹 변경은 이 행을 {@code SELECT … FOR UPDATE}로 잠가 직렬화한다.
 */
@Entity
@Table(name = "store_party")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreParty {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false, length = 4)
    private String code;

    @Column(nullable = false)
    private Long recorderMemberId;

    @Column(nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime disbandedAt;

    public StoreParty(Long storeId, String code, Long recorderMemberId, LocalDate businessDate, LocalDateTime now) {
        this.storeId = storeId;
        this.code = code;
        this.recorderMemberId = recorderMemberId;
        this.businessDate = businessDate;
        this.createdAt = now;
    }

    public boolean isActive() {
        return disbandedAt == null;
    }

    public void changeRecorder(Long memberId) {
        this.recorderMemberId = memberId;
    }

    public void disband(LocalDateTime now) {
        if (disbandedAt == null) disbandedAt = now;
    }
}
