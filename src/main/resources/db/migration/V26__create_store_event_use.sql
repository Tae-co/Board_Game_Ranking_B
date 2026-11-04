-- 1회용 쿠폰 이벤트: 손님이 상세 화면에서 "사용 완료"를 누르면 한 줄. 회원당 이벤트마다 한 번만
ALTER TABLE store_event ADD COLUMN IF NOT EXISTS one_time BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS store_event_use (
    id        BIGSERIAL PRIMARY KEY,
    event_id  BIGINT NOT NULL REFERENCES store_event (id) ON DELETE CASCADE,
    member_id BIGINT NOT NULL,
    used_at   TIMESTAMP(6) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_store_event_use ON store_event_use (event_id, member_id);
