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
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(unique = true)
    private String phoneNumber; // 전화번호 (로그인 아이디 역할)

    private String username; // 로그인 ID
    private String nickname; // 노출될 이름

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "rating", column = @Column(name = "overall_rating")),
        @AttributeOverride(name = "ratingDeviation", column = @Column(name = "overall_rd")),
        @AttributeOverride(name = "volatility", column = @Column(name = "overall_volatility"))
    })
    private GlickoStats overallStats = new GlickoStats(); // 전체 종합 랭킹용

    @Builder
    public Member(String phoneNumber, String username, String nickname) {
        this.phoneNumber = phoneNumber;
        this.username = username;
        this.nickname = nickname;
    }


}
