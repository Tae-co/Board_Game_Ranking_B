package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.board_game_back.Utils.RatingConstants;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시즌 롤오버 시 리셋 직전 랭킹을 남긴 사본. 한 번 쓰이면 바뀌지 않는다.
 *
 * <p><b>mu·sigma를 함께 들고 있는 이유:</b> {@code displayScore = (μ - 3σ) × 50 + 500}은
 * 두 값을 하나로 뭉개므로 역산이 불가능하다. 롤오버는 한 달에 한 번 자동으로 돌기 때문에
 * 잘못 돌았다는 걸 알았을 때는 이미 늦고, 원본 두 값이 없으면 되돌릴 방법이 아예 없다.
 *
 * <p><b>Room·Member를 연관관계로 잡지 않은 이유:</b> 방이나 멤버가 삭제돼도 시즌 기록은
 * 남아야 한다. FK가 없으므로 고아 행이 생길 수 있고, 조회하는 쪽에서 필터한다.
 *
 * <p><b>생성은 {@link #from} 하나로만 한다.</b> displayScore를 μ·σ와 따로 받으면
 * 서로 어긋난 행이 저장될 수 있다(로컬 테스트에서 실제로 재현했다). 세 값을 모두
 * {@link PlayerGameRating}에서 유도하면 구조적으로 불일치가 불가능하다.
 */
@Entity
@Table(name = "season_rank_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonRankSnapshot {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "season_rank_snapshot_id")
    private Long id;

    /** "2026-09" — 커뮤니티 region 타임존 기준의 연-월 */
    @Column(name = "season_key", nullable = false, length = 7)
    private String seasonKey;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "board_game_id", nullable = false)
    private Long boardGameId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "display_score", nullable = false)
    private double displayScore;

    @Column(name = "mu", nullable = false)
    private double mu;

    @Column(name = "sigma", nullable = false)
    private double sigma;

    @Column(name = "play_count", nullable = false)
    private int playCount;

    @Column(name = "win_count", nullable = false)
    private int winCount;

    @Column(name = "lose_count", nullable = false)
    private int loseCount;

    /** 어느 롤오버가 이 행을 썼는지. 잘못 돈 롤오버를 시각으로 특정할 때 쓴다. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 리셋 직전의 {@link PlayerGameRating}을 그대로 사진 찍는다.
     * room·boardGame·member·μ·σ·표시 점수·전적을 한 객체에서 뽑으므로 값이 어긋날 수 없다.
     *
     * @param rank 같은 방·게임 안에서의 순위 (1부터)
     */
    public static SeasonRankSnapshot from(String seasonKey, int rank, PlayerGameRating rating) {
        GlickoStats stats = rating.getGameStats();

        SeasonRankSnapshot snapshot = new SeasonRankSnapshot();
        snapshot.seasonKey = seasonKey;
        snapshot.roomId = rating.getRoom().getId();
        snapshot.boardGameId = rating.getBoardGame().getId();
        snapshot.memberId = rating.getMember().getId();
        snapshot.rank = rank;
        snapshot.displayScore = stats.getDisplayScore();
        snapshot.mu = stats.getRating();
        snapshot.sigma = stats.getRatingDeviation();
        snapshot.playCount = rating.getPlayCount();
        snapshot.winCount = rating.getWinCount();
        snapshot.loseCount = rating.getLoseCount();
        return snapshot;
    }

    /** 스냅샷의 μ·σ로 리셋을 되돌릴 때 쓴다. */
    public GlickoStats toStats() {
        return new GlickoStats(mu, sigma, RatingConstants.INITIAL_VOLATILITY);
    }
}
