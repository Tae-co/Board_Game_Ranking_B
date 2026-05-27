package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.*;

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
    private double bestDisplayScore = 1500.0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.MINUTES);

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "rating", column = @Column(name = "overall_rating")),
        @AttributeOverride(name = "ratingDeviation", column = @Column(name = "overall_rd")),
        @AttributeOverride(name = "volatility", column = @Column(name = "overall_volatility"))})
    private GlickoStats overallStats = new GlickoStats();

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

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
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
        this.bestDisplayScore = 1500.0;
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
