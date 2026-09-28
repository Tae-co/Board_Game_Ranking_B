package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.*;

import com.board_game_back.Utils.RatingConstants;
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
    private GlickoStats gameStats = new GlickoStats(); // 이 게임 전용 Glicko-2 랭킹

    private int playCount = 0;
    private int winCount = 0;
    private int loseCount = 0;

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

    public void applyDecay(double muDecay) {
        double currentMu = this.gameStats.getRating();
        double sigma = this.gameStats.getRatingDeviation();
        // 표시 점수 500(= 시작 점수)에 해당하는 μ 하한: (μ - 3σ)×50 + 500 = 500 ⟺ μ = 3σ
        // 리셋 직후는 μ=25, σ=25/3이라 3σ = 25 = μ — 갓 리셋된 사람은 하한에 정확히 걸려
        // decay가 아예 닿지 않는다. "500 위로 올라간 사람만 깎는다"가 공식 하나로 성립한다.
        double floorMu = RatingConstants.DISPLAY_SIGMA_FACTOR * sigma;
        double decayedMu = currentMu - muDecay;
        // decay는 벌어들인 것을 반납시킬 뿐 빚을 지우지 않는다. 이미 하한 밑이면(연패 등) 그대로 둔다.
        double newMu = decayedMu < floorMu ? Math.min(currentMu, floorMu) : decayedMu;
        this.gameStats.update(newMu, sigma, this.gameStats.getVolatility());
    }

    public void reset() {
        this.playCount = 0;
        this.winCount = 0;
        this.loseCount = 0;
        this.lastPlayedAt = null;
        this.gameStats.reset();
    }

    public void updateInitialRating(double newRating) {
        this.gameStats.update(newRating, this.gameStats.getRatingDeviation(), this.gameStats.getVolatility());
    }

}
