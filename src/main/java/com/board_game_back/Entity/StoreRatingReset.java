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
 * 신고로 한 매장 점수 초기화 (6b). 한 번에 고른 사람들은 같은 {@code batch}.
 * 되돌리면 {@code undoneAt}을 채우고 시즌 점수를 다시 계산한다. 당사자가 매장 홈 안내 카드를 닫으면 {@code seenAt}.
 */
@Entity
@Table(name = "store_rating_reset")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreRatingReset {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Long boardGameId;

    private Long memberId;

    private Long storeMatchId;

    @Column(nullable = false)
    private int beforeRating;

    @Column(nullable = false, length = 36)
    private String batch;

    @Column(nullable = false)
    private LocalDateTime resetAt;

    private LocalDateTime undoneAt;

    private LocalDateTime seenAt;

    public StoreRatingReset(Long storeId, Long boardGameId, Long memberId, Long storeMatchId, int beforeRating,
                            String batch, LocalDateTime now) {
        this.storeId = storeId;
        this.boardGameId = boardGameId;
        this.memberId = memberId;
        this.storeMatchId = storeMatchId;
        this.beforeRating = beforeRating;
        this.batch = batch;
        this.resetAt = now;
    }

    public boolean isActive() {
        return undoneAt == null;
    }

    public void undo(LocalDateTime now) {
        this.undoneAt = now;
    }

    public void markSeen(LocalDateTime now) {
        if (seenAt == null) seenAt = now;
    }
}
