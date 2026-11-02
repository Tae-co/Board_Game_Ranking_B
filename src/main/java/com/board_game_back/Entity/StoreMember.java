package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매장 멤버 + 가입 동의 기록 + 체크인. 시각은 UTC 벽시계.
 *
 * <p>체크인 만료 = max(QR 찍은 순간, 마지막 기록) + 3시간 (plan-store §1 매장 체크인).
 */
@Entity
@Table(name = "store_member", uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "member_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreMember {

    public static final Duration CHECKIN_DURATION = Duration.ofHours(3);

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private int consentVersion;

    @Column(nullable = false)
    private LocalDateTime consentedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    /** 마지막으로 QR을 찍은 순간 */
    private LocalDateTime checkedInAt;

    /** 마지막 기록 */
    private LocalDateTime lastRecordedAt;

    public StoreMember(Long storeId, Long memberId, int consentVersion, LocalDateTime now) {
        this.storeId = storeId;
        this.memberId = memberId;
        this.consentVersion = consentVersion;
        this.consentedAt = now;
        this.joinedAt = now;
    }

    /** 찍은 순간이 지금 체크인보다 늦을 때만 갱신한다 — 옛 티켓으로 체크인을 되돌리지 않게 */
    public void checkIn(LocalDateTime scannedAt) {
        if (checkedInAt == null || scannedAt.isAfter(checkedInAt)) checkedInAt = scannedAt;
    }

    /** null = 체크인한 적 없음 */
    public LocalDateTime checkinExpiresAt() {
        LocalDateTime base = latest(checkedInAt, lastRecordedAt);
        return base == null ? null : base.plus(CHECKIN_DURATION);
    }

    public boolean isCheckedIn(LocalDateTime now) {
        LocalDateTime expiresAt = checkinExpiresAt();
        return expiresAt != null && now.isBefore(expiresAt);
    }

    private static LocalDateTime latest(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
