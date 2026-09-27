-- 행동 로그 테이블.
--
-- 기존 테이블(member/room/match_record)은 '성공한 결과'만 남긴다. 그래서 전환율·이탈률의
-- 분모 — 시도했지만 완료하지 않은 사람 — 를 측정할 수 없다. 이 테이블이 그 분모다.
--
-- V13·V14는 feature/paywall-fake-door가 선점했으므로 V15를 쓴다.
CREATE TABLE IF NOT EXISTS user_event (
    id            BIGSERIAL PRIMARY KEY,
    member_id     BIGINT,             -- NULL 허용: 로그인 전 초대 랜딩 유입을 측정해야 한다
    anon_id       VARCHAR(64),        -- 기기 단위 UUID. 로그인 전/후 행동을 잇는다
    event_name    VARCHAR(40) NOT NULL,
    community_id  BIGINT,
    room_id       BIGINT,
    board_game_id BIGINT,             -- 게임별 점수판 이탈률 비교용
    props         JSONB,
    session_id    VARCHAR(64),
    platform      VARCHAR(16),        -- ios / android / web
    app_version   VARCHAR(20),
    created_at    TIMESTAMP(6) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_event_name_created ON user_event (event_name, created_at);
CREATE INDEX IF NOT EXISTS idx_user_event_member_created ON user_event (member_id, created_at);
CREATE INDEX IF NOT EXISTS idx_user_event_anon_created ON user_event (anon_id, created_at);
