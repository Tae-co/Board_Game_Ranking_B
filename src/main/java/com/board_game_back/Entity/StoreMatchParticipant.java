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

/** 판 참가자. {@code memberId == null}이면 탈퇴한 '나간 멤버'. */
@Entity
@Table(name = "store_match_participant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreMatchParticipant {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeMatchId;

    private Long memberId;

    private Integer placement;

    private Double gameScore;

    @Column(columnDefinition = "TEXT")
    private String scoresJson;

    private LocalDateTime confirmedAt;

    private Double ratingBefore;

    private Double ratingAfter;

    public StoreMatchParticipant(Long storeMatchId, Long memberId) {
        this.storeMatchId = storeMatchId;
        this.memberId = memberId;
    }

    public void setResult(int placement, Double gameScore, String scoresJson) {
        this.placement = placement;
        this.gameScore = gameScore;
        this.scoresJson = scoresJson;
    }

    public void confirm(LocalDateTime now) {
        if (confirmedAt == null) confirmedAt = now;
    }

    public void clearConfirm() {
        this.confirmedAt = null;
    }

    public void applyRating(double before, double after) {
        this.ratingBefore = before;
        this.ratingAfter = after;
    }

    public void anonymize() {
        this.memberId = null;
    }
}
