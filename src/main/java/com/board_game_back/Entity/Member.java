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

    private String username;
    private String nickname;
    private String password;
    private String role = "USER";

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "rating", column = @Column(name = "overall_rating")),
        @AttributeOverride(name = "ratingDeviation", column = @Column(name = "overall_rd")),
        @AttributeOverride(name = "volatility", column = @Column(name = "overall_volatility"))
    })
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

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    @Builder
    public Member(String phoneNumber, String username, String nickname, String password, String role) {
        this.phoneNumber = phoneNumber;
        this.username = username;
        this.nickname = nickname;
        this.password = password;
        this.role = role != null ? role : "USER";
        this.overallStats = new GlickoStats();
    }
}
