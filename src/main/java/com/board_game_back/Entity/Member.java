package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.*;

import com.board_game_back.Utils.RatingConstants;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(unique = true)
    private String phoneNumber;

    @Column(unique = true)
    private String socialId;

    private String username;
    private String nickname;
    private String password;
    private String role = "USER";

    @Column(name = "profile_image", columnDefinition = "TEXT")
    private String profileImage;

    @Column(name = "best_display_score")
    private double bestDisplayScore = RatingConstants.DISPLAY_OFFSET;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.MINUTES);

    /**
     * 모임장 Pro 만료 시각. null이면 무료. **결제 연동은 없다 — 프로토타입 플래그다.**
     *
     * 해지해도 이 값을 앞당기지 않는다. 낸 돈만큼은 쓰게 한다는 원칙이라
     * 해지는 "갱신 안 함"이고 만료는 이 시각이 지나는 것이다.
     */
    @Column(name = "pro_until")
    private LocalDateTime proUntil;

    /** MONTHLY | YEARLY. 구독 관리 화면의 플랜·금액 표시에만 쓴다. */
    @Column(name = "pro_billing", length = 10)
    private String proBilling;

    /** 해지 예약. true여도 proUntil 전까지는 Pro다. */
    @Column(name = "pro_canceled", nullable = false)
    private boolean proCanceled = false;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "rating", column = @Column(name = "overall_rating")),
        @AttributeOverride(name = "ratingDeviation", column = @Column(name = "overall_rd")),
        @AttributeOverride(name = "volatility", column = @Column(name = "overall_volatility"))})
    private GlickoStats overallStats = new GlickoStats();

    /** 구독 시작. 프로토타입이라 결제 검증 없이 만료 시각만 심는다. */
    public void grantPro(String billing, LocalDateTime until) {
        this.proBilling = billing;
        this.proUntil = until;
        this.proCanceled = false;
    }

    /** 해지 예약. proUntil은 건드리지 않는다 — 남은 기간은 그대로 쓴다. */
    public void cancelPro() {
        this.proCanceled = true;
    }

    /** 해지 취소(이어가기). */
    public void resumePro() {
        this.proCanceled = false;
    }

    public boolean isPro() {
        return proUntil != null && proUntil.isAfter(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    }

    public GlickoStats getOverallStats() {
        if (this.overallStats == null) {
            this.overallStats = new GlickoStats();
        }
        return this.overallStats;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    /** 토스 계정 연동 (추후 토스 로그인 도입 시 닉네임 계정에 socialId 연결) */
    public void linkSocialId(String socialId) {
        this.socialId = socialId;
    }

    public void updateBestDisplayScore(double newScore) {
        if (newScore > this.bestDisplayScore) {
            this.bestDisplayScore = newScore;
        }
    }

    public void setBestDisplayScore(double score) {
        this.bestDisplayScore = score;
    }

    public void resetBestDisplayScore() {
        this.bestDisplayScore = RatingConstants.DISPLAY_OFFSET;
    }

    @Builder
    public Member(String phoneNumber, String socialId, String username, String nickname,
        String password, String role) {
        this.phoneNumber = phoneNumber;
        this.socialId = socialId;
        this.username = username;
        this.nickname = nickname;
        this.password = password;
        this.role = role != null ? role : "USER";
        this.overallStats = new GlickoStats();
    }
}
