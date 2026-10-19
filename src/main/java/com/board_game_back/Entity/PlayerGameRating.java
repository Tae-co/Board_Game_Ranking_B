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
import java.time.LocalDateTime;
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
    private GlickoStats gameStats = new GlickoStats(); // 이 게임 전용 Elo 랭킹

    private int playCount = 0;
    private int winCount = 0;
    private int loseCount = 0;
    private int rebuyCount = 0; // 칩 게임: 첫 바이인 뒤에 다시 산 횟수 (판마다 바이인 횟수 - 1)

    private LocalDateTime lastPlayedAt;

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

    public void updateLastPlayedAt(LocalDateTime time) {
        this.lastPlayedAt = time;
    }

    public void reset() {
        this.playCount = 0;
        this.winCount = 0;
        this.loseCount = 0;
        this.rebuyCount = 0;
        this.lastPlayedAt = null;
        this.gameStats.reset();
    }

    /** 칩 게임 누적 손익. 아직 안 뛰었으면 0이다 (rating 컬럼의 초기값 500은 Elo용). 음수도 그대로 둔다. */
    public double chipNet() {
        return playCount == 0 ? 0 : gameStats.getRating();
    }

    /** 칩 게임 한 판을 반영한다. 바닥 없이 손익을 그대로 더한다. */
    public void applyChipResult(int chipDelta, int rebuys) {
        gameStats.update(chipNet() + chipDelta, gameStats.getRatingDeviation(), gameStats.getVolatility());
        rebuyCount += rebuys;
    }

    /** 랭킹·시즌 스냅샷 정렬 기준. 칩 게임은 음수 손익을 0으로 자르면 안 되므로 따로 본다. */
    public double rankingScore() {
        return boardGame != null && boardGame.isChipGame() ? chipNet() : gameStats.getDisplayScore();
    }

    public void updateInitialRating(double newRating) {
        this.gameStats.update(newRating, this.gameStats.getRatingDeviation(), this.gameStats.getVolatility());
    }

}
