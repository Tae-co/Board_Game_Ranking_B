-- 매장 모듈 (docs/plans/plan-store.md §3, plan-store-impl-p1.md §2).
-- 커뮤니티 테이블과 FK로 엮지 않는다.
-- member_id엔 FK를 두지 않는다: 회원 탈퇴가 member를 하드 삭제한다 (V19와 같은 이유).
-- 시각은 전부 UTC.

CREATE TABLE IF NOT EXISTS store (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    branch      VARCHAR(60),                 -- 지점 (예: 홍대점)
    address     VARCHAR(200) NOT NULL,
    region      VARCHAR(60)  NOT NULL,       -- RegionTimeZones 키 (예: South Korea)
    phone       VARCHAR(30),
    image_url   TEXT,
    open_time   TIME NOT NULL,               -- 매장 지역 시간 기준
    close_time  TIME NOT NULL,               -- open_time보다 이르면 자정을 넘김 (14:00–02:00)
    qr_secret   VARCHAR(64) NOT NULL,        -- 체크인 QR HMAC 키
    created_at  TIMESTAMP(6) NOT NULL,
    closed_at   TIMESTAMP(6),                -- 운영 끝냄. NULL = 운영 중
    CONSTRAINT ck_store_hours CHECK (open_time <> close_time)
);

-- 어느 매장의 STAFF인지. 계정 하나 = 매장 하나
CREATE TABLE IF NOT EXISTS store_staff (
    id          BIGSERIAL PRIMARY KEY,
    store_id    BIGINT NOT NULL REFERENCES store (id),
    member_id   BIGINT NOT NULL,
    assigned_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_store_staff_member UNIQUE (member_id)
);
CREATE INDEX IF NOT EXISTS idx_store_staff_store ON store_staff (store_id);

-- 매장 멤버 + 가입 동의 기록 + 체크인
CREATE TABLE IF NOT EXISTS store_member (
    id               BIGSERIAL PRIMARY KEY,
    store_id         BIGINT NOT NULL REFERENCES store (id),
    member_id        BIGINT NOT NULL,
    consent_version  INT NOT NULL,           -- 가입 동의 문구 버전
    consented_at     TIMESTAMP(6) NOT NULL,
    joined_at        TIMESTAMP(6) NOT NULL,
    checked_in_at    TIMESTAMP(6),           -- 마지막으로 QR을 찍은 순간
    last_recorded_at TIMESTAMP(6),           -- 마지막 기록 (경기 기록 단계부터 채움)
    CONSTRAINT uq_store_member UNIQUE (store_id, member_id)
);
CREATE INDEX IF NOT EXISTS idx_store_member_member ON store_member (member_id);

-- 매장 게임방 = 매장 × 공식 게임. closed = 이 매장에서 잠시 닫음
CREATE TABLE IF NOT EXISTS store_room (
    id            BIGSERIAL PRIMARY KEY,
    store_id      BIGINT NOT NULL REFERENCES store (id),
    board_game_id BIGINT NOT NULL REFERENCES board_game (board_game_id),
    closed        BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_store_room UNIQUE (store_id, board_game_id)
);
