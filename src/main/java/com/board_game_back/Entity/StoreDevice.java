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
 * 매장 기기 — 매장 TV(4) / 테이블 기기(4f). 사람 계정이 없고 기기 토큰(해시만 저장)으로만 연다 (plan-store §6.6).
 * {@code tokenHash}가 null이면 STAFF가 숫자를 입력했지만 기기가 아직 토큰을 받아 가지 않은 상태다.
 */
@Entity
@Table(name = "store_device")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreDevice {

    public enum Kind { TV, TABLE }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Kind kind;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastSeenAt;

    @Column(length = 64)
    private String lastIp;

    private LocalDateTime revokedAt;

    public StoreDevice(Long storeId, Kind kind, String name, LocalDateTime now) {
        this.storeId = storeId;
        this.kind = kind;
        this.name = name;
        this.createdAt = now;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public void issueToken(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public void seen(String ip, LocalDateTime now) {
        this.lastSeenAt = now;
        this.lastIp = ip;
    }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) revokedAt = now;
    }
}
