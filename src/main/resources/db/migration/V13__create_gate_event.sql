-- 페이크 도어 페이월(수익화 Approach A) 측정용 이벤트 로그.
--
-- 결제 로직은 없다. "어느 게이트에서 막혔고, 업그레이드를 눌렀는가"만 남겨서
-- 지불 의사가 운영 축에 있는지 랭킹 축에 있는지를 판별한다.
-- 축(axis)은 gate_key에서 파생되므로 저장하지 않는다 (GateKey enum이 매핑을 소유).
CREATE TABLE IF NOT EXISTS gate_event (
    id           BIGSERIAL PRIMARY KEY,
    member_id    BIGINT       NOT NULL,
    community_id BIGINT,
    gate_key     VARCHAR(40)  NOT NULL,
    action       VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMP(6) NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_gate_event_key_action ON gate_event (gate_key, action);
CREATE INDEX IF NOT EXISTS idx_gate_event_member ON gate_event (member_id);
