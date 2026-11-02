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

/**
 * 기기 화면(4j)에 뜨는 6자리. STAFF가 입력하면 {@code claimedDeviceId}가 채워지고,
 * 기기는 자기만 아는 {@code nonce}로 폴링해 토큰을 받아 간다 (받아 가면 이 줄은 지운다).
 */
@Entity
@Table(name = "store_pair_code")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorePairCode {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false, length = 64, unique = true)
    private String nonce;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private Long claimedDeviceId;

    public StorePairCode(String code, String nonce, LocalDateTime now, LocalDateTime expiresAt) {
        this.code = code;
        this.nonce = nonce;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public void claim(Long deviceId) {
        this.claimedDeviceId = deviceId;
    }
}
