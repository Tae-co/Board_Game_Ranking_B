package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 점수판 요청 한 건(REQUEST: 계산 방법·사진) 또는 공감(VOTE). 같은 사람은 게임마다 종류별 한 번 */
@Entity
@Table(name = "score_sheet_request_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoreSheetRequestEntry {

    public enum Kind { REQUEST, VOTE }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long gameRequestId;

    private Long memberId;

    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Kind kind;

    @Column(columnDefinition = "TEXT")
    private String howToScore;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ScoreSheetRequestEntry(Long gameRequestId, Long memberId, Long storeId, Kind kind, String howToScore,
                                  String imageUrl, LocalDateTime now) {
        this.gameRequestId = gameRequestId;
        this.memberId = memberId;
        this.storeId = storeId;
        this.kind = kind;
        this.howToScore = howToScore;
        this.imageUrl = imageUrl;
        this.createdAt = now;
    }
}
