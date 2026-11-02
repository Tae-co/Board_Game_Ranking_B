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
 * 매장 시즌 (plan-store §2.2 시즌, §6.4). 경계는 매장 영업일이고 종료일까지 포함한다.
 * 끝나면 {@code StoreSeasonService}가 순위를 스냅샷으로 남기고 매장 점수를 500으로 되돌린 뒤 {@code finalizedAt}을 채운다.
 */
@Entity
@Table(name = "store_season")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreSeason {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime finalizedAt;

    public StoreSeason(Long storeId, String name, LocalDate startDate, LocalDate endDate, LocalDateTime now) {
        this.storeId = storeId;
        this.createdAt = now;
        update(name, startDate, endDate);
    }

    public void update(String name, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public void finalizeAt(LocalDateTime now) {
        this.finalizedAt = now;
    }
}
