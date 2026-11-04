package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 끝난 시즌 결과 한 줄: 게임별 순위(RANK) / 최다 방문 / 최다 참여 / 최다승 / 최대 연승 / 최고 승률(value=%, games=판). 닉네임은 그때 값으로 남긴다 */
@Entity
@Table(name = "store_season_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreSeasonSnapshot {

    public enum Kind { RANK, MOST_VISITS, MOST_GAMES, MOST_WINS, BEST_STREAK, WIN_RATE }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long seasonId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Kind kind;

    private Long boardGameId;

    private Long memberId;

    @Column(length = 60)
    private String nickname;

    @Column(name = "\"rank\"")
    private Integer rank;

    @Column(nullable = false)
    private int value;

    private Integer games;

    public StoreSeasonSnapshot(Long seasonId, Kind kind, Long boardGameId, Long memberId, String nickname,
                               Integer rank, int value, Integer games) {
        this.seasonId = seasonId;
        this.kind = kind;
        this.boardGameId = boardGameId;
        this.memberId = memberId;
        this.nickname = nickname;
        this.rank = rank;
        this.value = value;
        this.games = games;
    }
}
