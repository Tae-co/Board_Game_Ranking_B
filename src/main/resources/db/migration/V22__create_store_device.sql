-- 매장 3단계: 매장 기기 등록·TV 디스플레이·테이블 QR (plan-store §2 2l·4·4f·4j·6g·6k, §6.6).
-- 기기엔 사람 로그인이 없다. 기기 토큰은 해시만 저장하고 요청마다 DB로 확인한다 (해제하면 즉시 끊김).

-- 매장 기기. token_hash가 NULL이면 STAFF가 숫자를 입력했지만 기기가 아직 토큰을 받아 가지 않은 상태
CREATE TABLE IF NOT EXISTS store_device (
    id           BIGSERIAL PRIMARY KEY,
    store_id     BIGINT NOT NULL REFERENCES store (id),
    kind         VARCHAR(8)  NOT NULL,       -- TV / TABLE
    name         VARCHAR(30) NOT NULL,
    token_hash   VARCHAR(64),                -- SHA-256 hex
    created_at   TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6),
    last_ip      VARCHAR(64),
    revoked_at   TIMESTAMP(6)                -- 해제 시각. NULL = 사용 중
);
CREATE INDEX IF NOT EXISTS idx_store_device_store ON store_device (store_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_store_device_token ON store_device (token_hash) WHERE token_hash IS NOT NULL;

-- 기기 화면(4j)의 6자리. nonce는 그 기기만 아는 값이라 폴링으로 토큰을 받아 가는 열쇠다
CREATE TABLE IF NOT EXISTS store_pair_code (
    id                BIGSERIAL PRIMARY KEY,
    code              VARCHAR(6)  NOT NULL,
    nonce             VARCHAR(64) NOT NULL,
    created_at        TIMESTAMP(6) NOT NULL,
    expires_at        TIMESTAMP(6) NOT NULL, -- 10분
    claimed_device_id BIGINT REFERENCES store_device (id),
    CONSTRAINT uq_store_pair_code_nonce UNIQUE (nonce)
);
CREATE INDEX IF NOT EXISTS idx_store_pair_code_code ON store_pair_code (code);

-- 매장 TV 화면 설정 (6k). 매장마다 한 줄, 없으면 기본값
CREATE TABLE IF NOT EXISTS store_tv_setting (
    store_id              BIGINT PRIMARY KEY REFERENCES store (id),
    screen_order          VARCHAR(100) NOT NULL,  -- 쉼표 구분: MVP,TOP10,QR,EVENT,CONTEST,SEASON (QR은 항상 포함)
    interval_sec          INT NOT NULL,           -- 5 / 10 / 15 / 20
    show_just_finished    BOOLEAN NOT NULL,
    top10_hidden_game_ids TEXT NOT NULL,          -- TOP 10에서 뺀 게임 id, 쉼표 구분. 다시 연 게임은 켜진 채로
    updated_at            TIMESTAMP(6) NOT NULL
);
