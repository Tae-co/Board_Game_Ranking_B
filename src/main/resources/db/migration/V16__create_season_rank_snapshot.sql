-- 시즌(월간) 롤오버 시 리셋 직전 랭킹을 사진처럼 남긴다.
-- 기획: docs/plans/plan-season-reset.md §3
--
-- mu·sigma를 함께 저장하는 이유:
--   display_score = (μ - 3σ) × 50 + 500 은 두 값을 하나로 뭉개므로 역산이 불가능하다.
--   롤오버는 한 달에 한 번 자동으로 돌기 때문에 잘못 돌았다는 걸 알았을 때는 이미 늦다.
--   원본 두 값을 남겨두면 복구가 UPDATE 한 방으로 끝난다.
--
-- lose_count까지 넣는 이유:
--   스냅샷 행이 RankingDto.GameRankingResponse와 필드가 같아야
--   프론트 랭킹 테이블(RankingTable.jsx)을 고치지 않고 재사용할 수 있다.
--
-- FK를 걸지 않는 이유:
--   방·멤버가 삭제돼도 시즌 기록은 남아야 한다 ("기록은 영원히 남는다").
--   고아 행은 조회 시 필터한다.

CREATE TABLE IF NOT EXISTS season_rank_snapshot (
    season_rank_snapshot_id BIGSERIAL PRIMARY KEY,
    season_key    VARCHAR(7)       NOT NULL,           -- '2026-09'
    room_id       BIGINT           NOT NULL,
    board_game_id BIGINT           NOT NULL,
    member_id     BIGINT           NOT NULL,
    rank          INTEGER          NOT NULL,
    display_score DOUBLE PRECISION NOT NULL,
    mu            DOUBLE PRECISION NOT NULL,
    sigma         DOUBLE PRECISION NOT NULL,
    play_count    INTEGER          NOT NULL DEFAULT 0,
    win_count     INTEGER          NOT NULL DEFAULT 0,
    lose_count    INTEGER          NOT NULL DEFAULT 0,
    -- 롤오버가 언제 이 행을 썼는지. 잘못 돈 롤오버를 시각으로 특정해서 되돌릴 때 쓴다.
    created_at    TIMESTAMP        NOT NULL DEFAULT NOW()
);

-- 멱등성의 근거. 롤오버가 두 번 돌아도 두 번 박히지 않는다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_season_rank_snapshot
    ON season_rank_snapshot (season_key, room_id, board_game_id, member_id);

-- 시즌 탭: 지난 시즌 순위표 조회
CREATE INDEX IF NOT EXISTS idx_season_rank_snapshot_lookup
    ON season_rank_snapshot (room_id, board_game_id, season_key, rank);

-- 프로필 트로피 선반 · 시즌별 내 점수 추이 조회
CREATE INDEX IF NOT EXISTS idx_season_rank_snapshot_member
    ON season_rank_snapshot (member_id, season_key);
