-- 매장 4단계: 시즌·신고·점수 초기화·점수판 요청 (plan-store §2 2g·3g·3l·4b·6b·6c·7·8, §6.4·§6.5).
-- member_id엔 FK를 두지 않는다 (V20과 같은 이유).

-- 다음 시즌을 정하는 방식: MONTHLY / QUARTERLY / CUSTOM (기본 매월)
ALTER TABLE store ADD COLUMN IF NOT EXISTS season_cycle VARCHAR(10) NOT NULL DEFAULT 'MONTHLY';

-- 매장 시즌. 경계는 영업일. finalized_at = 끝나서 순위를 남기고 점수를 리셋한 시각
CREATE TABLE IF NOT EXISTS store_season (
    id           BIGSERIAL PRIMARY KEY,
    store_id     BIGINT NOT NULL REFERENCES store (id),
    name         VARCHAR(40) NOT NULL,
    start_date   DATE NOT NULL,
    end_date     DATE NOT NULL,
    created_at   TIMESTAMP(6) NOT NULL,
    finalized_at TIMESTAMP(6),
    CONSTRAINT ck_store_season_dates CHECK (end_date >= start_date)
);
CREATE INDEX IF NOT EXISTS idx_store_season_store ON store_season (store_id, start_date);

-- 끝난 시즌의 결과. kind: RANK(게임별 순위) / MOST_VISITS / MOST_GAMES
CREATE TABLE IF NOT EXISTS store_season_snapshot (
    id            BIGSERIAL PRIMARY KEY,
    season_id     BIGINT NOT NULL REFERENCES store_season (id),
    kind          VARCHAR(12) NOT NULL,
    board_game_id BIGINT,
    member_id     BIGINT,
    nickname      VARCHAR(60),               -- 탈퇴해도 지난 결과는 남도록 그때 닉네임
    rank          INT,
    value         INT NOT NULL,              -- RANK: 점수 / MOST_VISITS: 일 / MOST_GAMES: 판
    games         INT
);
CREATE INDEX IF NOT EXISTS idx_store_season_snapshot_season ON store_season_snapshot (season_id);

-- 경기 신고 (3g). 같은 사람은 같은 경기를 한 번만. target = 그 경기 1등 (6b 묶음 기준)
CREATE TABLE IF NOT EXISTS store_report (
    id                 BIGSERIAL PRIMARY KEY,
    store_id           BIGINT NOT NULL REFERENCES store (id),
    store_match_id     BIGINT NOT NULL REFERENCES store_match (id),
    board_game_id      BIGINT NOT NULL,
    target_member_id   BIGINT,
    reporter_member_id BIGINT,
    reason             VARCHAR(16) NOT NULL,  -- NOT_PLAYED / THROWING / OTHER
    memo               VARCHAR(300),
    created_at         TIMESTAMP(6) NOT NULL,
    status             VARCHAR(10) NOT NULL DEFAULT 'OPEN',  -- OPEN / RESET / CLOSED
    resolved_at        TIMESTAMP(6),
    resolution_batch   VARCHAR(36),
    CONSTRAINT uq_store_report UNIQUE (store_match_id, reporter_member_id)
);
CREATE INDEX IF NOT EXISTS idx_store_report_store ON store_report (store_id, status);

-- 신고로 한 점수 초기화 (6b). undone_at = 되돌림, seen_at = 당사자가 매장 홈 안내 카드를 닫음
CREATE TABLE IF NOT EXISTS store_rating_reset (
    id             BIGSERIAL PRIMARY KEY,
    store_id       BIGINT NOT NULL REFERENCES store (id),
    board_game_id  BIGINT NOT NULL,
    member_id      BIGINT,
    store_match_id BIGINT REFERENCES store_match (id),
    before_rating  INT NOT NULL,
    batch          VARCHAR(36) NOT NULL,
    reset_at       TIMESTAMP(6) NOT NULL,
    undone_at      TIMESTAMP(6),
    seen_at        TIMESTAMP(6)
);
CREATE INDEX IF NOT EXISTS idx_store_rating_reset_store ON store_rating_reset (store_id);
CREATE INDEX IF NOT EXISTS idx_store_rating_reset_member ON store_rating_reset (member_id);

-- 점수판 요청 (2g·6c). 같은 게임 이름은 한 줄로 묶는다
CREATE TABLE IF NOT EXISTS score_sheet_game_request (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(60) NOT NULL,
    normalized_name   VARCHAR(60) NOT NULL,
    status            VARCHAR(10) NOT NULL DEFAULT 'REVIEW', -- REVIEW / MAKING / ADDED / HOLD
    created_at        TIMESTAMP(6) NOT NULL,
    last_requested_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_score_sheet_game_request UNIQUE (normalized_name)
);

-- 요청·공감 한 건. kind: REQUEST(내용 있음) / VOTE(공감). 같은 사람은 게임마다 종류별 한 번
CREATE TABLE IF NOT EXISTS score_sheet_request_entry (
    id              BIGSERIAL PRIMARY KEY,
    game_request_id BIGINT NOT NULL REFERENCES score_sheet_game_request (id),
    member_id       BIGINT,
    store_id        BIGINT REFERENCES store (id),
    kind            VARCHAR(8) NOT NULL,
    how_to_score    TEXT,
    image_url       TEXT,
    created_at      TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_score_sheet_request_entry UNIQUE (game_request_id, member_id, kind)
);
