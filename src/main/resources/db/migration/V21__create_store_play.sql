-- 매장 2단계: 오늘의 그룹 → 매장 게임방 판 → 전원 확인 → 매장 Elo (plan-store §2 2i·3·3b~3n).
-- member_id엔 FK를 두지 않는다 (V20과 같은 이유). 탈퇴하면 참가자 member_id는 NULL('나간 멤버').

-- 매장 기록 시간 (게임마다 하나, 모든 매장 같음). NULL이면 매장에서 시작할 수 없다. 커뮤니티는 안 읽는다
ALTER TABLE board_game ADD COLUMN IF NOT EXISTS min_minutes INT;
ALTER TABLE board_game ADD COLUMN IF NOT EXISTS max_minutes INT;

-- 오늘의 그룹. 만든 사람이 기록자. 활성 그룹(disbanded_at IS NULL) 안에서 코드는 매장마다 유일
CREATE TABLE IF NOT EXISTS store_party (
    id                 BIGSERIAL PRIMARY KEY,
    store_id           BIGINT NOT NULL REFERENCES store (id),
    code               VARCHAR(4) NOT NULL,
    recorder_member_id BIGINT NOT NULL,
    business_date      DATE NOT NULL,
    created_at         TIMESTAMP(6) NOT NULL,
    disbanded_at       TIMESTAMP(6)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_store_party_active_code ON store_party (store_id, code) WHERE disbanded_at IS NULL;

-- 그룹 소속. 한 사람은 한 번에 그룹 하나 (left_at IS NULL인 줄이 하나)
CREATE TABLE IF NOT EXISTS store_party_member (
    id          BIGSERIAL PRIMARY KEY,
    party_id    BIGINT NOT NULL REFERENCES store_party (id),
    member_id   BIGINT,
    joined_at   TIMESTAMP(6) NOT NULL,
    left_at     TIMESTAMP(6),
    left_reason VARCHAR(16)                   -- LEAVE / KICK / EXPIRED / DISBAND / MOVE / WITHDRAW
);
CREATE INDEX IF NOT EXISTS idx_store_party_member_party ON store_party_member (party_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_store_party_member_active ON store_party_member (member_id) WHERE left_at IS NULL;

-- 매장 판. 시작 때 저장 마감·영업 마감·기록 시간을 박아 둔다 (영업시간·기록 시간을 바꿔도 열린 판은 그대로)
CREATE TABLE IF NOT EXISTS store_match (
    id                    BIGSERIAL PRIMARY KEY,
    store_id              BIGINT NOT NULL REFERENCES store (id),
    store_room_id         BIGINT NOT NULL REFERENCES store_room (id),
    board_game_id         BIGINT NOT NULL,
    party_id              BIGINT NOT NULL REFERENCES store_party (id),
    recorder_member_id    BIGINT,
    status                VARCHAR(20) NOT NULL,  -- PLAYING / AWAITING_CONFIRM / DISPUTED / RECORDED / CANCELLED / EXPIRED
    business_date         DATE NOT NULL,
    min_minutes           INT NOT NULL,
    max_minutes           INT NOT NULL,
    started_at            TIMESTAMP(6) NOT NULL,
    save_deadline         TIMESTAMP(6) NOT NULL, -- min(시작 + 최대 시간, 영업 마감)
    closes_at             TIMESTAMP(6) NOT NULL, -- 시작한 영업일의 마감. 다시 저장도 이 전에만
    first_saved_at        TIMESTAMP(6),
    confirm_deadline      TIMESTAMP(6),
    disputed_by_member_id BIGINT,
    draft_json            TEXT,
    draft_version         INT NOT NULL DEFAULT 0,
    ended_at              TIMESTAMP(6),
    end_reason            VARCHAR(20)            -- CANCELLED / EXPIRED_MAX / EXPIRED_CLOSE / CONFIRMED / TIMEOUT
);
CREATE INDEX IF NOT EXISTS idx_store_match_room ON store_match (store_room_id, ended_at);
CREATE INDEX IF NOT EXISTS idx_store_match_status ON store_match (status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_store_match_open_party ON store_match (party_id)
    WHERE status IN ('PLAYING', 'AWAITING_CONFIRM', 'DISPUTED');

CREATE TABLE IF NOT EXISTS store_match_participant (
    id             BIGSERIAL PRIMARY KEY,
    store_match_id BIGINT NOT NULL REFERENCES store_match (id),
    member_id      BIGINT,
    placement      INT,
    game_score     DOUBLE PRECISION,
    scores_json    TEXT,
    confirmed_at   TIMESTAMP(6),
    rating_before  DOUBLE PRECISION,
    rating_after   DOUBLE PRECISION
);
CREATE INDEX IF NOT EXISTS idx_store_match_participant_match ON store_match_participant (store_match_id);
CREATE INDEX IF NOT EXISTS idx_store_match_participant_member ON store_match_participant (member_id);

-- 게임별 매장 Elo (시작 500)
CREATE TABLE IF NOT EXISTS store_rating (
    id             BIGSERIAL PRIMARY KEY,
    store_id       BIGINT NOT NULL REFERENCES store (id),
    board_game_id  BIGINT NOT NULL,
    member_id      BIGINT NOT NULL,
    rating         DOUBLE PRECISION NOT NULL,
    games          INT NOT NULL DEFAULT 0,
    wins           INT NOT NULL DEFAULT 0,
    last_played_at TIMESTAMP(6),
    CONSTRAINT uq_store_rating UNIQUE (store_id, board_game_id, member_id)
);
CREATE INDEX IF NOT EXISTS idx_store_rating_member ON store_rating (member_id);
