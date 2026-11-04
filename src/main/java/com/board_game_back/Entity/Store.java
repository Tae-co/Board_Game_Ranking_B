package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보드게임 카페 매장. 기획: {@code docs/plans/plan-store.md}.
 *
 * <p>영업시간은 매장 region 타임존의 벽시계 시각이다. {@code closeTime}이 {@code openTime}보다
 * 이르면 자정을 넘긴다 (14:00–02:00). 계산은 {@code StoreClock}이 한다.
 * 시각 컬럼({@code createdAt}, {@code closedAt})은 UTC 벽시계다.
 */
@Entity
@Table(name = "store")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 60)
    private String branch;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 60)
    private String region;

    @Column(length = 30)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @Column(nullable = false)
    private LocalTime openTime;

    @Column(nullable = false)
    private LocalTime closeTime;

    @Column(nullable = false, length = 64)
    private String qrSecret;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    /** 운영 끝낸 시각. null = 운영 중 */
    private LocalDateTime closedAt;

    /** 다음 시즌을 정하는 방식 (4단계): MONTHLY / QUARTERLY / CUSTOM */
    @Column(nullable = false, length = 10)
    private String seasonCycle = "MONTHLY";

    @Builder
    public Store(String name, String branch, String address, String region, String phone, String imageUrl,
                 LocalTime openTime, LocalTime closeTime, String qrSecret) {
        this.name = name;
        this.branch = branch;
        this.address = address;
        this.region = region;
        this.phone = phone;
        this.imageUrl = imageUrl;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.qrSecret = qrSecret;
    }

    public boolean isClosed() {
        return closedAt != null;
    }

    public void update(String name, String branch, String address, String region, String phone, String imageUrl,
                       LocalTime openTime, LocalTime closeTime) {
        this.name = name;
        this.branch = branch;
        this.address = address;
        this.region = region;
        this.phone = phone;
        this.imageUrl = imageUrl;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    /** 6g QR 비밀키 새로 만들기. 지금 QR이 모두 무효가 되고 기기 토큰은 그대로 */
    public void rotateQrSecret(String newSecret) {
        this.qrSecret = newSecret;
    }

    public void setSeasonCycle(String seasonCycle) {
        this.seasonCycle = seasonCycle;
    }

    public void close(LocalDateTime now) {
        if (closedAt == null) closedAt = now;
    }
}
