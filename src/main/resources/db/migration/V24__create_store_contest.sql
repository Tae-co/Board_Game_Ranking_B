-- 매장 5단계: 하루 대회 · 매장 이벤트 (plan-store §1 이벤트·대회 점수·대회 시작, §2 4d·4g·5a~5e·6j).
-- member_id엔 FK를 두지 않는다 (V20과 같은 이유).
--
-- 대회 판은 매장 판(store_match)을 그대로 쓴다. 참가자는 대회 전용 그룹(store_party.contest_id)으로 묶이고
-- 판은 store_match.contest_id·contest_round로 구분한다. 대회 판은 매장 Elo에 안 들어간다.

-- 하루 대회. status: RECRUITING / PLAYING / FINISHED / CANCELLED
-- end_reason: COMPLETED / SHORT_HANDED(남은 인원 < 최소 인원) / STAFF_CANCEL / DAY_OVER(그날 시작 안 됨) / STORE_CLOSED
CREATE TABLE IF NOT EXISTS store_contest (
    id                 BIGSERIAL PRIMARY KEY,
    store_id           BIGINT NOT NULL REFERENCES store (id),
    store_room_id      BIGINT NOT NULL REFERENCES store_room (id),
    board_game_id      BIGINT NOT NULL,
    title              VARCHAR(40) NOT NULL,
    starts_at          TIMESTAMP(6) NOT NULL,     -- UTC
    business_date      DATE NOT NULL,             -- starts_at이 속한 영업일. 이날 안에 시작 안 하면 취소
    capacity           INT NOT NULL,
    rounds             INT NOT NULL DEFAULT 3,
    prize_text         VARCHAR(80),
    status             VARCHAR(12) NOT NULL,
    end_reason         VARCHAR(16),
    party_id           BIGINT,                    -- 시작하면 대회 그룹
    recorder_member_id BIGINT,
    created_at         TIMESTAMP(6) NOT NULL,
    started_at         TIMESTAMP(6),
    ended_at           TIMESTAMP(6)
);
CREATE INDEX IF NOT EXISTS idx_store_contest_store ON store_contest (store_id, business_date);

-- 참가 신청. status: APPLIED / PLAYING / EXCLUDED(시작 때 도착 안 함) / LEFT(대회 중 매장 나감·탈퇴)
-- origin_party_id = 시작할 때 있던 오늘의 그룹 (끝나면 돌아감)
CREATE TABLE IF NOT EXISTS store_contest_entry (
    id              BIGSERIAL PRIMARY KEY,
    contest_id      BIGINT NOT NULL REFERENCES store_contest (id) ON DELETE CASCADE,
    member_id       BIGINT,
    applied_at      TIMESTAMP(6) NOT NULL,
    arrived_at      TIMESTAMP(6),
    status          VARCHAR(10) NOT NULL,
    origin_party_id BIGINT,
    left_at         TIMESTAMP(6)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_store_contest_entry ON store_contest_entry (contest_id, member_id);

-- 매장 이벤트 (음료 1+1 등). 게임방·랭킹 없이 매장 홈 카드와 TV 4d만
CREATE TABLE IF NOT EXISTS store_event (
    id           BIGSERIAL PRIMARY KEY,
    store_id     BIGINT NOT NULL REFERENCES store (id),
    title        VARCHAR(40) NOT NULL,
    benefit_text VARCHAR(300),
    image_url    VARCHAR(500),
    starts_on    DATE NOT NULL,
    ends_on      DATE NOT NULL,
    created_at   TIMESTAMP(6) NOT NULL,
    CONSTRAINT ck_store_event_dates CHECK (ends_on >= starts_on)
);
CREATE INDEX IF NOT EXISTS idx_store_event_store ON store_event (store_id, ends_on);

ALTER TABLE store_party ADD COLUMN IF NOT EXISTS contest_id BIGINT;
ALTER TABLE store_match ADD COLUMN IF NOT EXISTS contest_id BIGINT;
ALTER TABLE store_match ADD COLUMN IF NOT EXISTS contest_round INT;
CREATE INDEX IF NOT EXISTS idx_store_match_contest ON store_match (contest_id);

-- 대회 판은 참가자 확인 뒤 STAFF 확인(AWAITING_STAFF)을 기다린다. 이것도 열린 판
DROP INDEX IF EXISTS uq_store_match_open_party;
CREATE UNIQUE INDEX IF NOT EXISTS uq_store_match_open_party ON store_match (party_id)
    WHERE status IN ('PLAYING', 'AWAITING_CONFIRM', 'DISPUTED', 'AWAITING_STAFF');
