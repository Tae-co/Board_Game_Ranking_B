package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * STAFF 역할 계정을 매장에 지정한 기록. 계정 하나 = 매장 하나 ({@code member_id} UNIQUE).
 * 역할({@code Member.role = STAFF})이 없으면 이 행이 있어도 STAFF가 아니다 — {@code StoreAccessService}가 판정한다.
 */
@Entity
@Table(name = "store_staff")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreStaff {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false, unique = true)
    private Long memberId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime assignedAt = LocalDateTime.now(ZoneOffset.UTC);

    public StoreStaff(Long storeId, Long memberId) {
        this.storeId = storeId;
        this.memberId = memberId;
    }
}
