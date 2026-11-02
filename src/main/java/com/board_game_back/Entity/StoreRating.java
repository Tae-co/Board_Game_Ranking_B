package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import com.board_game_back.Utils.RatingConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 게임별 매장 Elo. 커뮤니티 {@code PlayerGameRating}과 따로 간다 (plan-store §3). */
@Entity
@Table(name = "store_rating", uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "board_game_id", "member_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreRating {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Long boardGameId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private double rating;

    @Column(nullable = false)
    private int games;

    @Column(nullable = false)
    private int wins;

    private LocalDateTime lastPlayedAt;

    public StoreRating(Long storeId, Long boardGameId, Long memberId) {
        this.storeId = storeId;
        this.boardGameId = boardGameId;
        this.memberId = memberId;
        this.rating = RatingConstants.INITIAL_RATING;
    }

    public void apply(double newRating, boolean won, LocalDateTime playedAt) {
        this.rating = newRating;
        this.games++;
        if (won) this.wins++;
        this.lastPlayedAt = playedAt;
    }

    /** 표시 점수 (커뮤니티 getDisplayScore와 같은 규칙) */
    public int displayScore() {
        if (Double.isNaN(rating)) return (int) RatingConstants.INITIAL_RATING;
        return (int) Math.round(Math.max(0, rating));
    }
}
