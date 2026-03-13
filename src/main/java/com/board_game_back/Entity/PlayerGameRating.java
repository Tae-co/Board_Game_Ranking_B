package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.*;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerGameRating {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "player_game_rating_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_game_id")
    private BoardGame boardGame;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room; // 어떤 방에서의 점수인지 기록

    @Embedded
    private GlickoStats gameStats = new GlickoStats(); // 이 게임 전용 Glicko-2 랭킹

    private int playCount = 0;
    private int winCount = 0;   // 추가
    private int loseCount = 0;  // 추가

    @Builder
    public PlayerGameRating(Member member, BoardGame boardGame, Room room) {
        this.member = member;
        this.boardGame = boardGame;
        this.room = room;
    }

    public void addPlayCount() {
        this.playCount++;
    }

    public void addWinCount() {
        this.winCount++;
    }

    public void addLoseCount() {
        this.loseCount++;
    }

    public void reset() {
        this.playCount = 0;
        this.winCount = 0;
        this.loseCount = 0;
        this.gameStats.reset();
    }

    public void updateInitialRating(double newRating) {
        this.gameStats.update(newRating, this.gameStats.getRatingDeviation(), this.gameStats.getVolatility());
    }

}
